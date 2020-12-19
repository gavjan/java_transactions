/*
 * University of Warsaw
 * Concurrent Programming Course 2020/2021
 * Java Assignment
 * 
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp1.base;

/**
 * The transaction manager interface your
 * solution has to implement.
 * 
 * @author Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
public interface TransactionManager {

	/** Start transaction for current thread
	 *
	 * @throws AnotherTransactionActiveException when the thread calling, has not completed his previous transaction yet.
	 */
	public void startTransaction(
	) throws
		AnotherTransactionActiveException;

	/** Performs an operation on the resource .
	 *
	 * @param rid resource identifier
	 * @param operation  operation to be performed
	 * @throws NoActiveTransactionException if the thread does not have an active transaction
	 * @throws UnknownResourceIdException when calling for a resource ID that is not under MT's control
	 * (with an argument corresponding to the uncontrolled resource's ID)
	 *
	 * @throws ActiveTransactionAborted if an active transaction has previously been canceled
	 * @throws ResourceOperationException must be passed to the calling thread
	 * @throws InterruptedException leaving both the resource and transaction state unchanged,
	 * and depending on the moment at which the interrupt occurred, the transaction may or may not be accessed.
	 * More specifically, if the interrupt occurred before or during the grant of access,
	 * the transaction does not obtain that access. Otherwise, access is granted.
	 */
	public void operateOnResourceInCurrentTransaction(
			ResourceId rid,
			ResourceOperation operation
	) throws
		NoActiveTransactionException,
		UnknownResourceIdException,
		ActiveTransactionAborted,
		ResourceOperationException,
		InterruptedException;

	/** Commit transaction. Any changes made by operations in that transaction become visible to other transactions.
	 *
	 * @throws NoActiveTransactionException when a thread does not have an active transaction
	 * @throws ActiveTransactionAborted when an active thread transaction has previously been canceled
	 */
	public void commitCurrentTransaction(
	) throws
		NoActiveTransactionException,
		ActiveTransactionAborted;

	/**
	 * Causes changes made by the operations of that transaction to be rolled back,
	 * i.e. the state of these resources is the same as when the transaction was initiated.
	 * Rollback always succeeds. When a thread does not have an active transaction, this is just an empty operation
	 */
	public void rollbackCurrentTransaction();

	/**
	 * @return true if and only if that thread has an active transaction
	 */
	public boolean isTransactionActive();

	/**
	 * @return true if and only if the thread has an active transaction, but the transaction has been canceled.
	 */
	public boolean isTransactionAborted();

}
