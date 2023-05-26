package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.ArrayList;
import java.util.List;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class PrintMetrics extends CodeDumper {
    public static class Metric {
        public long nblocks;
        public long nmethods;
        public long ninsts;

        Metric(long nblocks, long nmethods, long ninsts) {
            this.nblocks = nblocks;
            this.nmethods = nmethods;
            this.ninsts = ninsts;
        }
    }

    private static List<Metric> metricsStorage = new ArrayList<>();
    private static int STORAGE_MAX = 2;

    /**
     * Number of executed basic blocks.
     */
    private static long nblocks = 0;

    /**
     * Number of executed methods.
     */
    private static long nmethods = 0;

    /**
     * Number of executed instructions.
     */
    private static long ninsts = 0;

    public PrintMetrics(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void printMetrics() {
        System.out.println("Printing Metrics");
        for (Metric metric : metricsStorage) {
            System.out.println(String.format("[%s] Number of executed methods: %s", PrintMetrics.class.getSimpleName(), metric.nmethods));
            System.out.println(String.format("[%s] Number of executed basic blocks: %s", PrintMetrics.class.getSimpleName(), metric.nblocks));
            System.out.println(String.format("[%s] Number of executed instructions: %s", PrintMetrics.class.getSimpleName(), metric.ninsts));
        }
    }

    public static void resetMetrics() {
        nblocks = 0;
        ninsts = 0;
        nmethods = 0;
    }

    public static void resetStorage() {
        metricsStorage = new ArrayList<>();
    }

    public static void addMetric() {
        System.out.println("Added metric");
        metricsStorage.add(new Metric(nblocks, nmethods, ninsts));
        resetMetrics();
        if (metricsStorage.size() == STORAGE_MAX) {
            printMetrics();
            resetStorage();
        }
    }

    public static void incBasicBlock(int position, int length) {
        nblocks++;
        ninsts += length;
    }

    public static void incBehavior(String name) {
        nmethods++;
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", PrintMetrics.class.getName(), behavior.getLongName()));

        if (behavior.getName().equals("handle")) {
            behavior.insertAfter(String.format("%s.addMetric();", PrintMetrics.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", PrintMetrics.class.getName(), block.getPosition(), block.getLength()));
    }
}
