package nachos.ag;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.KThread;
import nachos.threads.Lock;

public class PrioritySchedulerGrader extends BasicTestGrader {
	@Override
	public void run() {
		Lock lock = new Lock();
		lock.acquire();

		boolean insStatus = Machine.interrupt().disable();

		ThreadHandler lockThread = forkNewThread(new Runnable() {
			@Override
			public void run() {
				lock.acquire();
				lock.release();
				done();
			}
		}, 3);
		lockThread.thread.setName("Locked thread");

		ThreadHandler midThread = forkNewThread(new Runnable() {
			@Override
			public void run() {
				alwaysYield(4);
				Lib.assertTrue(false, "priority donation error");
			}
		}, 2);
		midThread.thread.setName("Mid priority thread");

		ThreadHandler lowThread = forkNewThread(new Runnable() {
			@Override
			public void run() {
				alwaysYield(4);
				Lib.assertTrue(false, "priority donation error");
			}
		}, 1);
		lowThread.thread.setName("Low priority thread");

		Machine.interrupt().restore(insStatus);

		alwaysYield(3);

		insStatus = Machine.interrupt().disable();

		ThreadHandler LockThread2 = forkNewThread(new Runnable() {
			@Override
			public void run() {
				lock.acquire();
				System.out.println("in");
				lock.release();
			}
		}, 4);
		lowThread.thread.setName("Low priority thread2");

		Machine.interrupt().restore(insStatus);

		lock.release();
		KThread.yield();
	}

	private void alwaysYield(int n) {
		for (int i = 0; i < n; ++i) {
			KThread.yield();
		}
	}
}