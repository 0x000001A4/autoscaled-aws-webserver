package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class BasicBlockCache extends CodeDumper {

    private static class BblLastCall {
        public int position;
        public long last_bbl_call;

        public BblLastCall(int position, long last_bbl_call) {
            this.position = position;
            this.last_bbl_call = last_bbl_call;
        }
    }

    /**
     * Number of executed methods.
     */
    private static long nmethods = 0;

    /**
     * Number of executed instructions.
     */
    private static long ninsts = 0;

    private static long nblocks = 0;
    private static long bbl_hits = 0;
    private static long bbl_misses = 0;
    private static final long bbl_cache_size = 128;
    private static Map<Integer, BblLastCall> cache = new HashMap<Integer, BblLastCall>();

    public BasicBlockCache(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void incBasicBlock(int position, int length) {
        nblocks++;
        ninsts += length;
        if (!cache.containsKey(position)) {
            bbl_misses++;
            cache.put(position, new BblLastCall(position, nblocks));
        } else {
            BblLastCall bbl = cache.get(position);
            if (bbl.last_bbl_call + bbl_cache_size < nblocks) {
                bbl_hits++;
            } else {
                bbl_misses++;
            }
            bbl.last_bbl_call = nblocks;
        }
    }

    public static void incBehavior(String name) {
        nmethods++;
    }

    public static void printStatistics() {
        System.out.println(String.format("[%s] Number of executed methods: %s", BasicBlockCache.class.getSimpleName(), nmethods));
        System.out.println(String.format("[%s] Number of executed basic blocks: %s", BasicBlockCache.class.getSimpleName(), nblocks));
        System.out.println(String.format("[%s] Number of executed instructions: %s", BasicBlockCache.class.getSimpleName(), ninsts));
        System.out.println(String.format("[%s] Number of hits: %s", BasicBlockCache.class.getSimpleName(), bbl_hits));
        System.out.println(String.format("[%s] Number of misses: %s", BasicBlockCache.class.getSimpleName(), bbl_misses));
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", BasicBlockCache.class.getName(), behavior.getLongName()));

        if (behavior.getName().equals("handle")) {
            behavior.insertAfter(String.format("%s.printStatistics();", BasicBlockCache.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", BasicBlockCache.class.getName(), block.getPosition(), block.getLength()));
    }

}
