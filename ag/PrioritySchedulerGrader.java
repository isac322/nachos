package nachos.ag;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.ThreadedKernel;

public class PrioritySchedulerGrader extends BasicTestGrader {
	@Override
	public void run() {
		test1();

		done();
	}

	private void test1() {
		Lock lock = new Lock();
		lock.acquire();

		boolean insStatus = Machine.interrupt().disable();

		forkNewThread(new Runnable() {
			@Override
			public void run() {
				lock.acquire();
				lock.release();
			}
		}, 3, "Locked thread");

		forkNewThread(new Runnable() {
			@Override
			public void run() {
				alwaysYield(3);
				System.out.println("in1");
				Lib.assertTrue(false, "priority donation error");
			}
		}, 2, "Mid priority thread");

		forkNewThread(new Runnable() {
			@Override
			public void run() {
				alwaysYield(3);
				System.out.println("in1");
				Lib.assertTrue(false, "priority donation error");
			}
		}, 1, "Low priority thread");

		Machine.interrupt().restore(insStatus);

		alwaysYield(3);
		lock.release();
	}

	private void test3() {
		Lock l1 = new Lock(), l2 = new Lock(), l3 = new Lock();

		boolean insStatus = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(1);

		forkNewThread(new Runnable() {
			@Override
			public void run() {
				alwaysYield(3);
			}
		}, 1, "T1");

		final ThreadHandler t2 = forkNewThread(new Runnable() {
			@Override
			public void run() {
				boolean intStatus = Machine.interrupt().disable();
				ThreadedKernel.scheduler.setPriority(2);
				Machine.interrupt().restore(intStatus);

				l2.acquire();
				l1.acquire();

				l1.release();
				l2.release();
			}
		}, 2, "T2");

		forkNewThread(new Runnable() {
			@Override
			public void run() {
				l3.acquire();

				boolean intStatus = Machine.interrupt().disable();
				ThreadedKernel.scheduler.setPriority(3);
				Machine.interrupt().restore(intStatus);

				KThread.yield();
				l2.acquire();

				l2.release();
				l3.release();
			}
		}, 7, "T3");

		forkNewThread(new Runnable() {
			@Override
			public void run() {
				boolean intStatus = Machine.interrupt().disable();
				ThreadedKernel.scheduler.setPriority(t2.thread, 7);
				Machine.interrupt().restore(intStatus);

				l3.acquire();
				System.out.println("in");
				l3.release();
			}
		}, 5, "T5");

		Machine.interrupt().restore(insStatus);

		l1.acquire();
		KThread.yield();

		l1.release();

		alwaysYield(3);
	}

	private void alwaysYield(int n) {
		for (int i = 0; i < n; ++i) {
			KThread.yield();
		}
	}

	protected ThreadHandler forkNewThread(Runnable threadContent, int priority, String name) {
		KThread thread = new KThread(threadContent);
		ThreadHandler handler = getThreadHandler(thread);

		thread.setName(name);

		boolean intStatus = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(thread, priority);
		thread.fork();
		Machine.interrupt().restore(intStatus);

		return handler;
	}
}
