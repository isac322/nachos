package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 * <p>
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 * <p>
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 * <p>
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority <tt>true</tt> if this queue should
	 *                         transfer priority from waiting threads
	 *                         to the owning thread.
	 * @return a new priority thread queue.
	 */
	@Override
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	@Override
	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	@Override
	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	@Override
	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum &&
				priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	@Override
	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	@Override
	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		Machine.interrupt();
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		@Override
		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		@Override
		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		@Override
		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());

			KThread thread = pickNextThread();
			if (thread != null) {
				acquire(thread);    // thread가 돌게됨을 알림
			}
			return thread;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would
		 * return.
		 */
		protected KThread pickNextThread() {

			KThread result = null;
			int maxPriority = 0;

			for (KThread thread : waitQueue) {        // wait queue를 돌면서
				int tmpPriority = getThreadState(thread).getEffectivePriority();    // thread의 effective priority

				if (result == null || maxPriority < tmpPriority) {    // 선택된게 없거나 가장 높은 우선숭위를 가진 thread를 선택
					result = thread;
					maxPriority = tmpPriority;
				}
			}

			return result;
		}

		@Override
		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());

			System.out.print("\tPriority Queue : ");
			for (KThread thread : waitQueue) {
				System.out.print(thread + " ");
			}
			System.out.println("");
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;

		protected ThreadState lastThread = null;
		protected LinkedList<KThread> waitQueue = new LinkedList<>();
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue
	 * it's waiting for, if any.
	 *
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			int effectivePriority = priority;

			for (KThread donationThread : donationQueue) {        // donation할 queue를 모두 돌아보며
				int p = getThreadState(donationThread).getPriority();    // donationThread의 priority

				if (p > effectivePriority) {        // 더 높은 우선순위일 경우 donate
					effectivePriority = p;
				}
			}

			return effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param waitQueue the queue that the associated thread is
		 *                  now waiting on.
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			if (waitQueue.lastThread != null
					&& waitQueue.lastThread.thread == thread) {     // 돌던 thread가 wait하는 경우소 (lock됨)
				for (KThread waitThread : waitQueue.waitQueue) {    // priority queue의 모든 thread에 donation 알림
					ThreadState state = getThreadState(waitThread);
					state.donationQueue.add(thread);
				}
				locked = true;      // lock상태로 바꿈
			}

			waitQueue.waitQueue.add(thread);        // 기다리는 queue에 추가
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			waitQueue.waitQueue.remove(thread);        // 이 thread가 기다리던 queue에서 제거

			if (locked) {        // lock됐다가 깨어났을때
				locked = false;  // 상태를 locked이 아니게 바꾸고
				for (KThread waitThread : waitQueue.waitQueue) {    // priority queue의 모든 thread에 donation취소
					ThreadState state = getThreadState(waitThread);
					if (state.donationQueue.contains(thread)) {
						state.donationQueue.remove(thread);
					}
				}
			}

			waitQueue.lastThread = this;            // 이 thread가 선택됨을 알림
		}

		/**
		 * The thread with which this object is associated.
		 */
		protected KThread thread;
		/**
		 * The priority of the associated thread.
		 */
		protected int priority;
		/**
		 * 이 thread가 donation 받아야 할 큐
		 */
		protected LinkedList<KThread> donationQueue = new LinkedList<>();
		protected boolean locked = false;
	}
}
