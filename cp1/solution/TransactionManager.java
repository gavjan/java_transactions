package cp1.solution;

import cp1.base.*;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class TransactionManager implements cp1.base.TransactionManager {
    private final boolean DEBUG = true;
    private final LocalTimeProvider timeProvider;
    private final ConcurrentHashMap<Thread, Transaction> transaction_map = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ResourceId, Resource> resource_map = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ResourceId, Semaphore> semaphore_map = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> time_map = new ConcurrentHashMap<>(); //(maps thread-id to localTime)
    private final ConcurrentHashMap<Long, Long> graph = new ConcurrentHashMap<>(); // edges for wait-for graph (maps thread-id to thread-id)
    private final ConcurrentHashMap<ResourceId, Long> skip_flag = new ConcurrentHashMap<>(); // (maps ResourceId to thread-id)
    private final ConcurrentHashMap<Long, ResourceId> thread_currently_waiting = new ConcurrentHashMap<>(); // (maps thread-id to ResourceId)
    private final ConcurrentHashMap<ResourceId, Long> busy_resource_map = new ConcurrentHashMap<>(); // (maps ResourceId to thread-id)
    private final Semaphore graph_lock = new Semaphore(1);

    private void debug_out(String s) {
        if (DEBUG) System.out.println(s);
    }

    private Thread curr_thread() {
        return Thread.currentThread();
    }

    private Transaction curr_transaction() {
        return transaction_map.get(curr_thread());
    }

    private void detect_dead_lock(ResourceId rid) throws InterruptedException, ActiveTransactionAborted {
        if (!busy_resource_map.containsKey(rid)) return;

        long start_thread = curr_thread().getId();
        long t = start_thread;

        long latest_thread = t;
        while (graph.contains(t)) {
            if (is_later(t, latest_thread)) {
                latest_thread = t;
            }
            t = graph.get(t);
            if (t == start_thread) {
                fix_dead_lock(latest_thread);
                break;
            }
        }
    }

    private void fix_dead_lock(long thread) throws ActiveTransactionAborted {
        debug_out("\t[DEADLOCK][" + curr_thread().getId() + "] detected deadlock, " + thread + " will be aborted");
        if (thread == curr_thread().getId()) {
            graph_lock.release();
            curr_transaction().abort();
            curr_thread().interrupt();
            throw new ActiveTransactionAborted();
        }
        ResourceId rid = thread_currently_waiting.get(thread);
        //padding

        skip_flag.put(rid, thread);
        semaphore_map.get(rid).release();
    }

    private boolean is_later(long t, long latest) {
        long t_time = time_map.get(t);
        long latest_time = time_map.get(latest);
        if (t_time == latest_time) {
            if (t == latest) return true;
            return t > latest;
        }
        return t_time > latest_time;
    }

    public TransactionManager(Collection<Resource> resources, LocalTimeProvider timeProvider) {
        this.timeProvider = timeProvider;

        for (Resource x : resources) {
            resource_map.put(x.getId(), x);
            semaphore_map.put(x.getId(), new Semaphore(1, true));
        }
    }

    @Override
    public void startTransaction() throws AnotherTransactionActiveException {
        long startTime = timeProvider.getTime();

        if (transaction_map.containsKey(curr_thread())) throw new AnotherTransactionActiveException();

        Transaction t = new Transaction();

        debug_out("[" + curr_thread().getId() + "] started at " + startTime);
        time_map.put(curr_thread().getId(), startTime);
        transaction_map.put(curr_thread(), t);
    }

    @Override
    public void operateOnResourceInCurrentTransaction(ResourceId rid, ResourceOperation operation) throws
            NoActiveTransactionException,
            UnknownResourceIdException,
            ActiveTransactionAborted,
            ResourceOperationException,
            InterruptedException {
        if (!isTransactionActive()) throw new NoActiveTransactionException();
        if (!resource_map.containsKey(rid)) throw new UnknownResourceIdException(rid);
        if (isTransactionAborted()) throw new ActiveTransactionAborted();


        Semaphore semaphore = semaphore_map.get(rid);
        Resource resource = resource_map.get(rid);

        if (!curr_transaction().touchedResource(resource)) {
            debug_out("[" + curr_thread().getId() + "] operates on resource " + rid + " (he never touched it)");
            graph_lock.acquire();
            debug_out("[" + curr_thread().getId() + "] entering");


            if (busy_resource_map.containsKey(rid)) {
                debug_out("[" + curr_thread().getId() + "]" + " resource " + rid + " is busy by " + busy_resource_map.get(rid));
                graph.put(curr_thread().getId(), busy_resource_map.get(rid));
                detect_dead_lock(resource.getId());

                thread_currently_waiting.put(curr_thread().getId(), rid);
            } else {
                busy_resource_map.put(rid, curr_thread().getId());
            }
            debug_out("[" + curr_thread().getId() + "] leaving");
            graph_lock.release();

            semaphore.acquire();
            while (skip_flag.containsKey(rid)) {
                if (skip_flag.get(rid) == curr_thread().getId()) {
                    skip_flag.remove(rid);
                    curr_transaction().abort();
                    curr_thread().interrupt();
                    throw new ActiveTransactionAborted();
                } else {
                    semaphore.release();
                    semaphore.acquire();
                }
            }


            graph_lock.acquire();

            thread_currently_waiting.remove(curr_thread().getId());
            graph.remove(curr_thread().getId());
            graph_lock.release();



        } else debug_out("[" + curr_thread().getId() + "] operates on resource " + rid + " (touched it already)");
        curr_transaction().execOperation(resource, operation);
    }

    @Override
    public void commitCurrentTransaction() throws NoActiveTransactionException, ActiveTransactionAborted {
        if (!isTransactionActive()) throw new NoActiveTransactionException();
        if (isTransactionAborted()) throw new ActiveTransactionAborted();

        try {
            graph_lock.acquire();
        } catch (InterruptedException ignored) {
        }
        curr_transaction().commit(semaphore_map, busy_resource_map);
        transaction_map.remove(curr_thread());
        time_map.remove(curr_thread().getId());
        graph.remove(curr_thread().getId());
        graph_lock.release();
        debug_out("Thread " + curr_thread().getId() + " commited his transaction");
    }

    @Override
    public void rollbackCurrentTransaction() {
        if (isTransactionActive()) {
            try {
                graph_lock.acquire();
            } catch (InterruptedException ignored) {
            }
            curr_transaction().rollBack(semaphore_map, busy_resource_map);
            transaction_map.remove(curr_thread());
            time_map.remove(curr_thread().getId());

            graph.remove(curr_thread().getId());
            graph_lock.release();


            debug_out("Thread " + curr_thread().getId() + " rolled back his transaction");
        } else debug_out("Thread " + curr_thread().getId() + " empty rollback");

    }

    @Override
    public boolean isTransactionActive() {
        return transaction_map.containsKey(curr_thread());
    }

    @Override
    public boolean isTransactionAborted() {
        if (!isTransactionActive()) return false;

        return curr_transaction().isTransactionAborted();
    }
}