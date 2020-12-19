package cp1.solution;

import cp1.base.*;

import java.util.HashSet;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class Transaction {
    public AtomicBoolean isTransactionAborted = new AtomicBoolean();
    private final Stack<Resource> resourceStack = new Stack<>();
    private final Stack<ResourceOperation> operationStack = new Stack<>();

    public void execOperation(Resource resource, ResourceOperation operation) throws ResourceOperationException {
        operation.execute(resource);
        operationStack.add(operation);
        resourceStack.add(resource);
    }


    public void rollBack(ConcurrentHashMap<ResourceId, Semaphore> semaphore_map, ConcurrentHashMap<ResourceId, Long> busy_resource_map) {
        HashSet<ResourceId> already_cleared = new HashSet<>();
        while (!resourceStack.empty() && !operationStack.empty()) {
            Resource resource = resourceStack.pop();
            ResourceOperation operation = operationStack.pop();

            releaseIfNecessary(semaphore_map, busy_resource_map, already_cleared, resource.getId());


            operation.undo(resource);
        }
    }

    private void releaseIfNecessary(ConcurrentHashMap<ResourceId, Semaphore> semaphore_map, ConcurrentHashMap<ResourceId, Long> busy_resource_map,
                                    HashSet<ResourceId> already_cleared, ResourceId rid) {
        if (!already_cleared.contains(rid)) {
            already_cleared.add(rid);
            semaphore_map.get(rid).release();
            busy_resource_map.remove(rid);
        }
    }

    public Transaction() {
        isTransactionAborted.set(false);
    }

    public boolean touchedResource(Resource resource) {
        return resourceStack.search(resource) != -1;
    }

    public void commit(ConcurrentHashMap<ResourceId, Semaphore> semaphore_map, ConcurrentHashMap<ResourceId, Long> busy_resource_map) {
        HashSet<ResourceId> already_cleared = new HashSet<>();
        while (!resourceStack.empty()) {
            Resource resource = resourceStack.pop();
            releaseIfNecessary(semaphore_map, busy_resource_map, already_cleared, resource.getId());
        }
    }

    public boolean isTransactionAborted() {
        return isTransactionAborted.get();
    }

    public void abort() {
        isTransactionAborted.set(true);
    }
}
