package parallelprpd.pipeline;

/**
 *
 * @author jstar
 */
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class BufferFactory {

    private static final int BUFFER_COUNT = 4;
    private static final int BUFFER_SIZE = 1024 * 1024;
    
    private static final Buffer [] buffers = new Buffer[BUFFER_COUNT];

    private static final BlockingQueue<Buffer> pool =
            new ArrayBlockingQueue<>(BUFFER_COUNT);

    static {
        for (int i = 0; i < BUFFER_COUNT; i++) {
            buffers[i] = new Buffer(BUFFER_SIZE);
            pool.add(buffers[i]);
        }
    }
    
    public static int bufferSize() {
        return BUFFER_SIZE;
    }

    private BufferFactory() {}
    
    public static void reset() {
        pool.clear();
        for( Buffer b : buffers ) {
            returnToPool(b);
        }        
    }

    public static Buffer acquire(int consumerCount) {
        try {
            Buffer b = pool.take();
            b.resetForUse(consumerCount);
            return b;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for buffer", e);
        }
    }

    static void returnToPool(Buffer b) {
        b.clear();
        if (!pool.offer(b)) {
          //  throw new IllegalStateException("Buffer returned twice?");
        }
    }
}