package nachos.userprog;

import java.io.EOFException;
import java.util.Arrays;
import java.util.LinkedList;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Kernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.KThread;
import nachos.threads.ThreadedKernel;
import nachos.vm.VMProcess;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		openfiles = new FileDescriptor[maxFiles];
		openfiles[0] = new FileDescriptor(UserKernel.console.openForReading(), "STDIN"); // std in
		openfiles[1] = new FileDescriptor(UserKernel.console.openForWriting(), "STDOUT"); // std out
		pid = UserKernel.getPid();
		UserKernel.addProcess(pid, this);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
			return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
			return new VMProcess ();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this); 
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int vpn = Processor.pageFromAddress(vaddr);
		int addressOffset = Processor.offsetFromAddress(vaddr);
		TranslationEntry entry = pageTable[vpn];
		entry.used = true;
		int ppn = entry.ppn;
		int paddr = (ppn*pageSize) + addressOffset;
		int amount = length;	//number we want to read
		int dataRead = 0;	//number we really read

		// data may be huge and stored in several pages
		while (amount > 0 && vpn < numPages) {
			int amountReadable = pageSize - addressOffset;	// how many bytes left in current page
			int amountRead = Math.min(length, amountReadable);
			System.arraycopy(memory, paddr, data, offset, amountRead);
			amount -= amountRead;
			// direct to next physical page
			vpn++;
			offset += amountRead;
			dataRead += amountRead;
			if (vpn < numPages) {
				entry = pageTable[vpn];
				ppn = entry.ppn;
				paddr = ppn*pageSize;
			}
		}
		return dataRead;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		int vpn = Processor.pageFromAddress(vaddr);
		int addressOffset = Processor.offsetFromAddress(vaddr);
		TranslationEntry entry = pageTable[vpn];
		if (entry.readOnly)
			return 0;

		entry.used = true;
		entry.dirty = true;
		int ppn = entry.ppn;
		int paddr = (ppn*pageSize) + addressOffset;
		int amount = length;	//number we want to write
		int dataWrite = 0;	//number we really write

		// data may be huge and stored in several pages
		while (amount > 0 && vpn < numPages) {
			int amountWritable = pageSize - addressOffset;	// how many bytes left in current page
			int amountWrite = Math.min(length, amountWritable);
			System.arraycopy(data, offset, memory, paddr, amountWrite);
			amount -= amountWrite;
			// direct to next physical page
			vpn++;
			offset += amountWrite;
			dataWrite += amountWrite;
			if (vpn < numPages) {
				entry = pageTable[vpn];
				ppn = entry.ppn;
				paddr = ppn*pageSize;
			}
		}
		return dataWrite;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
			pageTable[i] = new TranslationEntry(i, UserKernel.getFreeMemory(), true, false, false, false);

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
			+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				TranslationEntry e = pageTable[vpn];
				e.readOnly = section.isReadOnly();
				section.loadPage(i, e.ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < numPages; i++) {
			UserKernel.addFreeMemory(pageTable[i].ppn);
			pageTable[i].valid = false;
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < Processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Look for a spare file descriptor
	 * @return
	 */
	private int findUseableFD() {
		for (int i = 2; i < maxFiles; i++) {
			if (openfiles[i] != null)
				i++;
			else
				return i;
		}
		return -1;
	}

	/**
	 * Look for file descriptor according to the file name
	 */

	private int findFD(String filename) {
		for (int i = 2; i < maxFiles; i++) {
			if (openfiles[i].filename == filename)
				return i;
		}
		return -1;
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if (pid == ROOT) {

			Machine.halt();

			Lib.assertNotReached("Machine.halt() did not halt machine!");
		}
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private void handleExit(int status) {
		for (int i = 0; i < maxFiles; i++) {
			if (openfiles[i] != null)
				handleClose(i);
		}

		while (children != null && !children.isEmpty()) {
			int childPid = children.removeFirst();
			UserProcess child = UserKernel.getProcessById(childPid);
			if (child != null)
				child.ppid = 0;
		}

		exitStatus = status;
		unloadSections();
		if (UserKernel.getCurrentProcesses() == 1 || pid == ROOT)
			Kernel.kernel.terminate();
		else {
			if (ppid == 0)
				UserKernel.removeProcess(pid);
			KThread.currentThread().finish();
		}
	}

	/**
	 * Handle the exec() system call
	 */
	private int handleExec(int fileLocation, int argc, int argvLocation) {
		if (argc < 0)
			return -1;

		String filename = readVirtualMemoryString(fileLocation, maxLength);
		if (filename == null)
			return -1;
		if (!filename.endsWith("coff"))
			return -1;
		String args[] = new String[argc];
		byte[] addr = new byte[4];
		for (int i = 0; i < argc; i++) {
			int bytesRead = readVirtualMemory(argvLocation + i * 4, addr);
			if (bytesRead != 4)
				return -1;
			int argAddress = Lib.bytesToInt(addr, 0);
			args[i] = readVirtualMemoryString(argAddress, maxLength);
		}
		UserProcess child = UserProcess.newUserProcess();
		child.ppid = pid;
		children.add(child.pid);
		boolean successful = child.execute(filename, args);
		return successful ? child.pid : -1;
	}

	/**
	 * Handle the join() system call
	 */
	private int handleJoin(int pid, int statusLocation) {
		boolean isChild = false;
		for (int cpid : children) {
			if (cpid == pid) {
				isChild = true;
				break;
			}
		}
		if (!isChild)
			return -1;
		UserProcess child = UserKernel.getProcessById(pid);
		if (child == null) {
			return -1;
		}

		child.thread.join();
		byte[] childExitStatus = new byte[4];
		childExitStatus = Lib.bytesFromInt(child.exitStatus);
		UserKernel.removeProcess(child.pid);
		int bytesWrite = writeVirtualMemory(statusLocation, childExitStatus);
		if (bytesWrite != 4)
			return 0;
		else
			return 1;
	}

	/**
	 * Handle the creat() system call.
	 */
	private int handleCreate(int fileLocation) {
		String filename = readVirtualMemoryString(fileLocation, maxLength);
		OpenFile f = UserKernel.fileSystem.open(filename, true);
		if (f == null)
			return -1;
		int fd = findUseableFD();
		if (fd == -1)
			return -1;
		openfiles[fd] = new FileDescriptor(f, filename);
		return fd;
	}

	/**
	 * Handle the open() system call.
	 */
	private int handleOpen(int fileLocation) {
		String filename = readVirtualMemoryString(fileLocation, maxLength);
		OpenFile f = UserKernel.fileSystem.open(filename, false);
		if (f == null) {
			return -1;
		}
		int fd = findUseableFD();
		if (fd == -1)
			return -1;
		openfiles[fd] = new FileDescriptor(f, filename);
		return fd;
	}

	/**
	 * Handle the read() system call.
	 */
	private int handleRead(int fd, int bufferLocation, int count) {
		if (fd < 0 || fd >= maxFiles || openfiles[fd] == null || count < 0 || fd == 1)
			return -1;
		FileDescriptor filedes = openfiles[fd];
		byte[] data = new byte[count];
		int num;
		if (fd != 0)
			num = filedes.file.read(filedes.pos, data, 0, count);
		else
			num = filedes.file.read(data, 0, count);
		if (num < 0) {
			return -1;
		}
		if (fd > 1)
			filedes.pos += num;
		writeVirtualMemory(bufferLocation, data);
		return num;
	}

	/**
	 * Handle the write() system call.
	 */
	private int handleWrite(int fd, int bufferLocation, int count) {
		if (fd < 0 || fd >= maxFiles || openfiles[fd] == null || count < 0 || fd == 0)
			return -1;
		FileDescriptor fileDes = openfiles[fd];
		byte[] data = new byte[count];
		int bytesRead = readVirtualMemory(bufferLocation, data);
		int num;
		if (fd != 1)
			num = fileDes.file.write(fileDes.pos, data, 0, bytesRead);
		else
			num = fileDes.file.write(data, 0, bytesRead);
		if (num < 0) {
			return -1;
		}
		if (fd > 1)
			fileDes.pos += num;
		return num;
	}

	/**
	 * Handle the close() system call.
	 */
	private int handleClose(int fd) {
		if (fd < 0 || fd >= maxFiles || openfiles[fd] == null)
			return -1;
		FileDescriptor fileDes = openfiles[fd];
		boolean res = true;
		fileDes.file.close();
		if (fileDes.toDelete)
			res = UserKernel.fileSystem.remove(fileDes.filename);
		openfiles[fd] = null;
		return res ? 0 : -1;
	}

	/**
	 * Handle the unlink() system call.
	 */
	private int handleUnlink(int fileLocation) {
		String filename = readVirtualMemoryString(fileLocation, maxLength);
		int fd = findFD(filename);
		boolean res = true;
		if (fd == -1) {
			res = UserKernel.fileSystem.remove(filename);
		}
		else {
			openfiles[fd].toDelete = true;
		}
		return res ? 0 : -1;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			handleExit(a0);
			return 0;
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			handleExit(1);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	/**
	 * added class, represents the file descriptor
	 * @author suica
	 *
	 */
	public class FileDescriptor{
		public FileDescriptor(OpenFile file, String filename) {
			this.file = file;
			pos = 0;
			toDelete = false;
			this.filename = filename;
		}
		public int pos;
		public OpenFile file;
		public boolean toDelete;
		public String filename;
	}

	/** added constant, maximal length of strings passed as arguments*/
	private static final int maxLength = 256;

	/** added constant, maximal number of open files of each process*/
	private static final int maxFiles = 16;

	/** added member, all the open files of the current process*/
	private FileDescriptor[] openfiles;

	/** added member, pid*/
	private int pid;

	/** added member, parent pid*/
	private int ppid;

	/** added member, list of children*/
	private LinkedList<Integer> children = new LinkedList<>();

	private final int ROOT = 1;

	/** added member, thread of current process*/
	private UThread thread;

	private int exitStatus;
}
