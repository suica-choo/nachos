package nachos.userprog;

import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.Coff;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.threads.KThread;
import nachos.threads.ThreadedKernel;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());
		
		// initialize page table
		pageTable = new LinkedList<>();
		for (int i = 0; i < Machine.processor().getNumPhysPages(); i++)
			pageTable.add(i);

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

/*		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');
		System.out.println("");
*/
	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		KThread.currentThread().finish();
	}
	
	/**
	 * return the index of a free page
	 * @return
	 */
	public static int getFreeMemory() {
		Machine.interrupt().disable();
		int page = pageTable.removeFirst();
		Machine.interrupt().enable();
		return page;
	}
	
	/**
	 * allocate freed memory
	 */
	public static void addFreeMemory(int page) {
		Lib.assertTrue(page >= 0 && page < Machine.processor().getNumPhysPages());
		Machine.interrupt().disable();
		pageTable.addLast(page);
		Machine.interrupt().enable();	
	}
	
	/**
	 * get pid
	 */
	public static int getPid() {
		int pid = 0;
		Machine.interrupt().disable();
		pid = nextPid++;
		Machine.interrupt().enable();
		return pid;
	}
	
	/**
	 * get process by pid
	 */
	public static UserProcess getProcessById(int pid) {
		return processMap.get(pid);
	}
	
	/**
	 * get current number of processes 
	 */
	public static int getCurrentProcesses() {
		return processMap.size();
	}
	
	/**
	 * add new process
	 */
	public static void addProcess(int pid, UserProcess process) {
		Machine.interrupt().disable();
		processMap.put(pid, process);
		Machine.interrupt().enable();
	}
	
	/**
	 * remove process
	 */
	public static void removeProcess(int pid) {
		Machine.interrupt().disable();
		processMap.remove(pid);
		Machine.interrupt().enable();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;
	
	/** added global variable, stores useable physical memory*/
	private static LinkedList<Integer> pageTable;
	
	/** added global variable, points to next pid*/
	private static int nextPid = 1;
	
	private static HashMap<Integer, UserProcess> processMap = new HashMap<>(); 
}
