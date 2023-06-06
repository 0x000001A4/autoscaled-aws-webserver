package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class PrintMetrics extends CodeDumper {
    
    public static boolean instrumenting = false;

    public static class Metric {
        // Metric information
        public String serviceName;
        public Object[] args;
        // Metric result
        public long nblocks;
        public long nmethods;
        public long ninsts;

        Metric(String serviceName, Object[] args, long nblocks, long nmethods, long ninsts) {
            this.serviceName = serviceName;
            this.args = args;
            this.nblocks = nblocks;
            this.nmethods = nmethods;
            this.ninsts = ninsts;
        }
    }

    private static ConcurrentLinkedQueue<Metric> metricsStorage = new ConcurrentLinkedQueue<>();
    private static int STORAGE_MAX = 2;

    /**
     * Number of executed basic blocks.
     */
    private static ConcurrentMap<Long, Long> nblocksMap = new ConcurrentHashMap<>();

    /**
     * Number of executed methods.
     */
    private static ConcurrentMap<Long, Long> nmethodsMap = new ConcurrentHashMap<>();

    /**
     * Number of executed instructions.
     */
    private static ConcurrentMap<Long, Long> ninstsMap = new ConcurrentHashMap<>();

    public PrintMetrics(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static List<PrintMetrics.Metric> returnMetrics() {
        List<PrintMetrics.Metric> metrics = new ArrayList<Metric>();
        while (metricsStorage.size() > 0) {
            metrics.add(metricsStorage.poll());
        }
        return metrics;
    }

    public static Object[] mapFirstArgToLength(Object[] args) {
        Object[] newArgs = new Object[args.length];
        newArgs[0] = ((byte[])args[0]).length;
        System.arraycopy(args, 1, newArgs, 1, args.length-1);
        return newArgs;
    }

    public static void printMetrics() {
        System.out.println("--- Printing Metrics ---");
        for (Metric metric : metricsStorage) {
            System.out.println(String.format("Printing metrics from service: %s  with args: ", metric.serviceName));
            for (int i = 0; i < metric.args.length; i++) {
                System.out.print(metric.args[i]);
                System.out.print(", ");
            }
            System.out.println();
            System.out.println(String.format("[%s] Number of executed methods: %s", PrintMetrics.class.getSimpleName(), metric.nmethods));
            System.out.println(String.format("[%s] Number of executed basic blocks: %s", PrintMetrics.class.getSimpleName(), metric.nblocks));
            System.out.println(String.format("[%s] Number of executed instructions: %s", PrintMetrics.class.getSimpleName(), metric.ninsts));
        }
    }

    public static void resetMetrics() {
        long id = Thread.currentThread().getId();
        nblocksMap.put(id, 0l);
        ninstsMap.put(id, 0l);
        nmethodsMap.put(id, 0l);
    }

    public static void resetStorage() {
        metricsStorage = new ConcurrentLinkedQueue<>();
    }

    public static void addMetric(String serviceName, Object[] args) {
        System.out.println("------------------------------------------------------");
        System.out.println(String.format("Added Metric for service: %s  with args:", serviceName));
        for (int i = 0; i < args.length; i++) {
            System.out.print(args[i]);
            System.out.print(", ");
        }
        System.out.println("\n------------------------------------------------------");

        long id = Thread.currentThread().getId();
        long nblocks = nblocksMap.get(id);
        long nmethods = nmethodsMap.get(id);
        long ninsts = ninstsMap.get(id);

        metricsStorage.add(new Metric(serviceName, args, nblocks, nmethods, ninsts));
        resetMetrics();
        if (metricsStorage.size() == STORAGE_MAX) {
            printMetrics();
            resetStorage();
        }
        instrumenting = false;
    }

    public static void incBasicBlock(int position, int length) {
        long id = Thread.currentThread().getId();
        nblocksMap.putIfAbsent(id, 0l);
        long nblocks = nblocksMap.get(id);
        nblocksMap.put(id, nblocks + 1);

        ninstsMap.putIfAbsent(id, 0l);
        long ninsts = ninstsMap.get(id);
        ninstsMap.put(id, ninsts + length);
    }

    public static void incBehavior(String name) {
        long id = Thread.currentThread().getId();
        nmethodsMap.putIfAbsent(id, 0l);
        long nmethods = nmethodsMap.get(id);
        nmethodsMap.put(id, nmethods + 1);
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        if (instrumenting) {
            super.transform(behavior);
            behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", PrintMetrics.class.getName(), behavior.getLongName()));
        }

        if (behavior.getName().equals("instrumentThis")) {
            switch (behavior.getDeclaringClass().getSimpleName()) {
                case "BaseCompressingHandler":
                    instrumenting = true;
                    behavior.insertAfter(String.format("%s.addMetric(\"%s\", %s.mapFirstArgToLength($args));",
                        PrintMetrics.class.getName(),
                        behavior.getDeclaringClass().getSimpleName(),
                        PrintMetrics.class.getName()
                    ));
                    break;
                default:
                    instrumenting = true;    
                    behavior.insertAfter(String.format("%s.addMetric(\"%s\", $args);",
                        PrintMetrics.class.getName(),
                        behavior.getDeclaringClass().getSimpleName()
                    ));
                    break;
            }
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        if (instrumenting) {
            super.transform(block);
            block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", PrintMetrics.class.getName(), block.getPosition(), block.getLength()));
        }
    }
}
