import java.util.concurrent.CountDownLatch;

public class Benchmark {

    interface ConcurrentSet {
        boolean add(int value);
        boolean remove(int value);
        boolean contains(int value);
    }

    static class CoarseAdapter implements ConcurrentSet {
        private final CoarseList list = new CoarseList();
        public boolean add(int v) { return list.add(v); }
        public boolean remove(int v) { return list.remove(v); }
        public boolean contains(int v) { return list.contains(v); }
    }

    static class FineAdapter implements ConcurrentSet {
        private final FineList list = new FineList();
        public boolean add(int v) { return list.add(v); }
        public boolean remove(int v) { return list.remove(v); }
        public boolean contains(int v) { return list.contains(v); }
    }

    // Same mixed workload pattern as Main.java (1/3 add, 1/3 contains, 1/3 remove)
    static double runTrial(ConcurrentSet list, int numberOfThreads, int operationsPerThread) throws InterruptedException {
        Thread[] threads = new Thread[numberOfThreads];
        CountDownLatch startGate = new CountDownLatch(1);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadID = i;
            threads[i] = new Thread(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < operationsPerThread; j++) {
                    int value = (threadID * 1000) + (j % 1000);
                    if (j % 3 == 0) {
                        list.add(value);
                    } else if (j % 3 == 1) {
                        list.contains(value);
                    } else {
                        list.remove(value);
                    }
                }
            });
        }

        for (Thread t : threads) t.start();
        long startTime = System.nanoTime();
        startGate.countDown();
        for (Thread t : threads) t.join();
        long endTime = System.nanoTime();

        return (endTime - startTime) / 1_000_000.0;
    }

    public static void main(String[] args) throws InterruptedException {
        int[] threadCounts = {2, 4, 8, 16};
        int operationsPerThread = 20000;
        int trialsPerConfig = 5;

        System.out.println("Threads,Implementation,Trial,Time(ms)");

        double[][] coarseTimes = new double[threadCounts.length][trialsPerConfig];
        double[][] fineTimes = new double[threadCounts.length][trialsPerConfig];

        for (int t = 0; t < threadCounts.length; t++) {
            int threads = threadCounts[t];

            for (int trial = 0; trial < trialsPerConfig; trial++) {
                ConcurrentSet coarse = new CoarseAdapter();
                double coarseMs = runTrial(coarse, threads, operationsPerThread);
                coarseTimes[t][trial] = coarseMs;
                System.out.printf("%d,Coarse,%d,%.3f%n", threads, trial + 1, coarseMs);
            }

            for (int trial = 0; trial < trialsPerConfig; trial++) {
                ConcurrentSet fine = new FineAdapter();
                double fineMs = runTrial(fine, threads, operationsPerThread);
                fineTimes[t][trial] = fineMs;
                System.out.printf("%d,Fine,%d,%.3f%n", threads, trial + 1, fineMs);
            }
        }

        System.out.println();
        System.out.println("Summary (average of " + trialsPerConfig + " trials, " + operationsPerThread + " ops/thread)");
        System.out.printf("%-10s %-22s %-22s%n", "Threads", "Coarse-Grained (ms)", "Fine-Grained (ms)");
        for (int t = 0; t < threadCounts.length; t++) {
            double coarseAvg = average(coarseTimes[t]);
            double fineAvg = average(fineTimes[t]);
            System.out.printf("%-10d %-22.2f %-22.2f%n", threadCounts[t], coarseAvg, fineAvg);
        }
    }

    static double average(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }
}
