package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		lock = new Lock();
		speaker = new Condition(lock);
		listener = new Condition(lock);
		wordReady = false;
		numOfSpe = 0;
		numOfLis = 0;
		word = 0;
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		lock.acquire();

		numOfSpe++;
		while (numOfLis == 0 || wordReady)
			speaker.sleep();
		this.word = word;
		wordReady = true;
		listener.wakeAll();
		numOfSpe--;

		lock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		lock.acquire();

		numOfLis++;
		while (!wordReady) {
			speaker.wakeAll();
			listener.sleep();
		}
		int word = this.word;
		wordReady = false;
		numOfLis--;

		lock.release();
		return word;
	}

	public static void commTest6() {
		final Communicator com = new Communicator();
		final long times[] = new long[4];
		final int words[] = new int[2];
		KThread speaker1 = new KThread( new Runnable () {
			public void run() {
				com.speak(4);
				times[0] = Machine.timer().getTime();
			}
		});
		speaker1.setName("S1");
		KThread speaker2 = new KThread( new Runnable () {
			public void run() {
				com.speak(7);
				times[1] = Machine.timer().getTime();
			}
		});
		speaker2.setName("S2");
		KThread listener1 = new KThread( new Runnable () {
			public void run() {
				times[2] = Machine.timer().getTime();
				words[0] = com.listen();
			}
		});
		listener1.setName("L1");
		KThread listener2 = new KThread( new Runnable () {
			public void run() {
				times[3] = Machine.timer().getTime();
				words[1] = com.listen();
			}
		});
		listener2.setName("L2");

		speaker1.fork(); speaker2.fork(); listener1.fork(); listener2.fork();
		speaker1.join(); speaker2.join(); listener1.join(); listener2.join();

		Lib.assertTrue(words[0] == 4, "Didn't listen back spoken word.");
		Lib.assertTrue(words[1] == 7, "Didn't listen back spoken word.");
		Lib.assertTrue(times[0] > times[2], "speak() returned before listen() called.");
		Lib.assertTrue(times[1] > times[3], "speak() returned before listen() called.");
		System.out.println("commTest6 successful!");
	}
	
	public static void commTest1() {
		final Communicator com = new Communicator();
		final long times[] = new long[4];
		final int words[] = new int[2];
		KThread speaker1 = new KThread( new Runnable () {
			public void run() {
				com.speak(4);
				times[0] = Machine.timer().getTime();
			}
		});
		speaker1.setName("S1");
		KThread listener1 = new KThread( new Runnable () {
			public void run() {
				times[1] = Machine.timer().getTime();
				words[0] = com.listen();
			}
		});
		listener1.setName("L1");

		speaker1.fork(); listener1.fork();
		speaker1.join(); listener1.join();

		Lib.assertTrue(words[0] == 4, "Didn't listen back spoken word.");
		Lib.assertTrue(times[0] > times[1], "speak() returned before listen() called.");
		System.out.println("commTest1 successful!");
		System.out.println("times[0] is : " + times[0]);
		System.out.println("times[1] is : " + times[1]);
	}
	
	public static void commTest2() {
		final Communicator com = new Communicator();
		final long times[] = new long[4];
		final int words[] = new int[2];
		KThread speaker1 = new KThread( new Runnable () {
			public void run() {
				com.speak(4);
				times[0] = Machine.timer().getTime();
			}
		});
		speaker1.setName("S1");
		KThread listener1 = new KThread( new Runnable () {
			public void run() {
				times[1] = Machine.timer().getTime();
				words[0] = com.listen();
			}
		});
		listener1.setName("L1");

		speaker1.fork(); listener1.fork();
		listener1.join(); speaker1.join();

		Lib.assertTrue(words[0] == 4, "Didn't listen back spoken word.");
		Lib.assertTrue(times[0] > times[1], "speak() returned before listen() called.");
		System.out.println("commTest2 successful!");
		System.out.println("times[0] is : " + times[0]);
		System.out.println("times[1] is : " + times[1]);
	}

    public static void selfTest() {
        // place calls to simpler Communicator tests that you implement here
    	commTest1();
    	commTest2();
        commTest6();
    }

	private Condition speaker;
	private Condition listener;
	private Lock lock;
	private boolean wordReady;
	private int numOfSpe;
	private int numOfLis;
	private int word;
}
