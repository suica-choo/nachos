package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.Machine;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
		pq = new PriorityQueue<>();
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();

		long nowTime = Machine.timer().getTime();
		while (!pq.isEmpty() && pq.peek().wakeTime < nowTime) {
			pq.poll().thread.ready();
		}
		
		KThread.currentThread().yield();
		
		Machine.interrupt().restore(intStatus);

	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		boolean intStatus = Machine.interrupt().disable();

		long wakeTime = Machine.timer().getTime() + x;
		pq.add(new TimerThread(KThread.currentThread(), wakeTime));
		KThread.currentThread().sleep();
		
		Machine.interrupt().restore(intStatus);
	}

	private static PriorityQueue<TimerThread> pq;

	private class TimerThread {
		public KThread thread;
		public long wakeTime;
		public TimerThread(KThread thread, long wakeTime) {
			this.thread = thread;
			this.wakeTime = wakeTime;
		}
	}


	// Add Alarm testing code to the Alarm class

	public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	public static void alarmTest2() {
	    KThread t1 = new KThread(new Runnable() {
	        public void run() {
	            long time1 = Machine.timer().getTime();
	            System.out.println("Thread calling wait at time:" + time1);
	            ThreadedKernel.alarm.waitUntil(10000);
	            System.out.println("Thread woken up after:" + (Machine.timer().getTime() - time1));
	        }
	    });
	    t1.fork();
	    t1.join();
	}

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		alarmTest1();
		alarmTest2();
		// Invoke your other test methods here ...
	}

}
