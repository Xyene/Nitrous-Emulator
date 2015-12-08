package nitrous;

import nitrous.lcd.Interpolator;
import nitrous.lcd.LCD;
import nitrous.mbc.Memory;
import nitrous.renderer.IRenderManager;
import nitrous.sound.SoundManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;

import static nitrous.Emulator.RegisterPair.*;

public class Emulator
{

    public Emulator(Cartridge cartridge)
    {
        this.cartridge = cartridge;
        this.mmu = cartridge.createController(this);
        this.lcd = new LCD(this);
        this.sound = new SoundManager(this);
        sound.updateClockSpeed(clockSpeed);
        reset();
    }

    private boolean paused = false;
    private Semaphore executeLock = new Semaphore(1);

    public boolean timerEnabled;
    public int timerPeriod;

    public final Memory mmu;
    public final LCD lcd;
    public final SoundManager sound;

    public final Cartridge cartridge;

    public boolean interruptsEnabled;

    /**
     * The DMG has 4 flag registers, zero, subtract, half-carry and carry.
     * Half-carry is only ever used for the DAA instruction. Half-carry is usually carry over lower nibble, and carry
     * is over bit 7.
     */
    public final short F_Z = 0x80;
    public final short F_N = 0x40;
    public final short F_H = 0x20;
    public final short F_C = 0x10;

    /**
     * Program counter.
     */
    public int pc;
    /**
     * CPU registers, any write to F is masked with (F_Z | F_N | F_H | F_C), so the other bits always read as 0 (even
     * if you specifically try to write to them). (HL) is indirect memory access.
     */
    public int A, B, C, D, E, F, H, L;

    /**
     * Stack pointer.
     */
    public int SP;

    /**
     * Whether the CPU is currently halted: if so, it will still operate at 4MHz, but will not execute any instructions
     * until an interrupt is executed. This is for "power saving".
     */
    public boolean cpuHalted = false;

    enum RegisterPair
    {
        BC, DE, HL, SP;

        public static final RegisterPair[] byValue = {BC, DE, HL, SP};
    }

    /**
     * This is... obvious?
     */
    public boolean buttonRight, buttonLeft, buttonStart, buttonSelect, buttonUp, buttonDown, buttonA, buttonB;

    public int getRegisterPair(RegisterPair object)
    {
        switch (object)
        {
            case BC:
                return (B << 8) | C;
            case DE:
                return (D << 8) | E;
            case HL:
                return (H << 8) | L;
            case SP:
                return SP;
        }
        throw new UnsupportedOperationException("" + object);
    }

    /**
     * Like getRegisterPair, except 0x3 maps to AF.
     *
     * @param object the register pair id
     * @return the value of the register pair
     */
    public int getRegisterPair2(RegisterPair object)
    {
        switch (object)
        {
            case BC:
                return (B << 8) | C;
            case DE:
                return (D << 8) | E;
            case HL:
                return (H << 8) | L;
            case SP:
                // Some instructions care about AF instead of SP, which is why this method exists
                return (A << 8) | F;
        }
        throw new UnsupportedOperationException("" + object);
    }

    public void setRegisterPair(RegisterPair object, short hi, short lo)
    {
        hi &= 0xff;
        lo &= 0xff;
        switch (object)
        {
            case BC:
                B = hi;
                C = lo;
                break;
            case DE:
                D = hi;
                E = lo;
                break;
            case HL:
                H = hi;
                L = lo;
                break;
            case SP:
                SP = (hi << 8) | lo;
                break;
        }
    }

    public void setRegisterPair(RegisterPair object, int val)
    {
        short hi = (short) ((val >> 8) & 0xFF);
        short lo = (short) (val & 0xFF);
        setRegisterPair(object, hi, lo);
    }

    public void setRegisterPair2(RegisterPair object, int hi, int lo)
    {
        hi &= 0xff;
        lo &= 0xff;
        switch (object)
        {
            case BC:
                B = hi;
                C = lo;
                break;
            case DE:
                D = hi;
                E = lo;
                break;
            case HL:
                H = hi;
                L = lo;
                break;
            case SP:
                A = hi;
                // Other bits don't actually exist
                F = lo & (F_C | F_H | F_N | F_Z);
                break;
        }
    }

    public void reset()
    {
        // On startup, a CGB has 11h in A, a normal GB has 01h
        A = cartridge.isColorGB ? 0x11 : 0x01;
        F = 0xB0;

        setRegisterPair(BC, 0x0013);
        setRegisterPair(DE, 0x00D8);
        setRegisterPair(HL, 0x014D);

        SP = 0xFFFE;
        pc = 0x100;

        //Arrays.fill(mmu.registers, (byte)0x00);
        for (int i = 0; i < 0x100; i++)
        {
            setIO(i, 0);
        }
        setIO(0x10, 0x80);
        setIO(0x11, 0xbf);
        setIO(0x12, 0xf3);
        setIO(0x14, 0xbf);
        setIO(0x16, 0x3f);
        setIO(0x19, 0xbf);
        setIO(0x1a, 0x7f);
        setIO(0x1b, 0xff);
        setIO(0x1c, 0x9f);
        setIO(0x1e, 0xbf);
        setIO(0x20, 0xff);
        setIO(0x23, 0xbf);
        setIO(0x24, 0x77);
        setIO(0x25, 0xf3);
        setIO(0x26, cartridge.isSuperGB ? 0xf0 : 0xf1);
        setIO(0x40, 0x91);
        setIO(0x47, 0xfc);
        setIO(0x48, 0xff);
        setIO(0x49, 0xff);
    }

    /**
     * This shit is used for JMP instructions etc.
     *
     * @param which the flag mask.
     */
    public boolean getConditionalFlag(int which)
    {
        switch (which & 0x7)
        {
            case 0b100:
                return (F & F_Z) == 0;
            case 0b101:
                return (F & F_Z) != 0;
            case 0b110:
                return (F & F_C) == 0;
            case 0b111:
                return (F & F_C) != 0;
        }
        return false;
    }

    public int getRegister(int r)
    {
        switch (r)
        {
            case 0b111:
                return A;
            case 0b000:
                return B;
            case 0b001:
                return C;
            case 0b010:
                return D;
            case 0b011:
                return E;
            case 0b100:
                return H;
            case 0b101:
                return L;
            case 0b110:
                // Indirect memory access
                return getByte((H << 8) | L);
        }
        return 0;
    }

    public void setRegister(int r, int val)
    {
        val &= 0xff;
        switch (r)
        {
            case 0b111:
                A = val;
                break;
            case 0b000:
                B = val;
                break;
            case 0b001:
                C = val;
                break;
            case 0b010:
                D = val;
                break;
            case 0b011:
                E = val;
                break;
            case 0b100:
                H = val;
                break;
            case 0b101:
                L = val;
                break;
            case 6:
                // Indirect memory access
                setByte((H << 8) | L, val);
                break;
        }
    }

    /**
     * Fires interrupts if interrupts are enabled.
     */
    public void fireInterrupts()
    {
        // If interrupts are disabled (via the DI instruction), ignore this call
        if (!interruptsEnabled) return;

        // Flag of which interrupts should be triggered
        byte triggeredInterrupts = mmu.registers[R.R_TRIGGERED_INTERRUPTS];

        // Which interrupts the program is actually interested in, these are the ones we will fire
        int enabledInterrupts = mmu.registers[R.R_ENABLED_INTERRUPTS];

        // If this is nonzero, then some interrupt that we are checking for was triggered
        if ((triggeredInterrupts & enabledInterrupts) != 0)
        {
            pushWord(pc);

            // This is important
            interruptsEnabled = false;

            // Interrupt priorities are vblank > lcdc > tima overflow > serial transfer > hilo
            if (isInterruptTriggered(R.VBLANK_BIT))
            {
                pc = R.VBLANK_HANDLER_ADDRESS;
                triggeredInterrupts &= ~R.VBLANK_BIT;
            } else if (isInterruptTriggered(R.LCDC_BIT))
            {
                pc = R.LCDC_HANDLER_ADDRESS;
                triggeredInterrupts &= ~R.LCDC_BIT;
            } else if (isInterruptTriggered(R.TIMER_OVERFLOW_BIT))
            {
                pc = R.TIMER_OVERFLOW_HANDLER_ADDRESS;
                triggeredInterrupts &= ~R.TIMER_OVERFLOW_BIT;
            } else if (isInterruptTriggered(R.SERIAL_TRANSFER_BIT))
            {
                pc = R.SERIAL_TRANSFER_HANDLER_ADDRESS;
                triggeredInterrupts &= ~R.SERIAL_TRANSFER_BIT;
            } else if (isInterruptTriggered(R.HILO_BIT))
            {
                pc = R.HILO_HANDLER_ADDRESS;
                triggeredInterrupts &= ~R.HILO_BIT;
            }
            mmu.registers[R.R_TRIGGERED_INTERRUPTS] = triggeredInterrupts;
        }
    }

    /**
     * Checks whether a particular interrupt is enabled.
     *
     * @param interrupt
     * @return
     */
    public boolean isInterruptTriggered(int interrupt)
    {
        return (mmu.registers[R.R_TRIGGERED_INTERRUPTS] & mmu.registers[R.R_ENABLED_INTERRUPTS] & interrupt) != 0;
    }

    public void setInterruptTriggered(int interrupt)
    {
        mmu.registers[R.R_TRIGGERED_INTERRUPTS] |= interrupt;
    }

    public boolean isInterruptEnabled(int interrupt)
    {
        return (mmu.registers[R.R_ENABLED_INTERRUPTS] & interrupt) != 0;
    }

    long divCycle = 0;
    public long timerCycle = 0;
    public int clockSpeed = 4194304;
    public boolean emulateSpeed = true;

    public void updateInterrupts(long delta)
    {
        // The DIV register increments at 16KHz, and resets to 0 after
        divCycle += delta;

        if (divCycle >= 256)
        {
            divCycle -= 256;
            // This is... probably correct
            mmu.registers[R.R_DIV]++;
        }

        // The Timer is similar to DIV, except that when it overflows it triggers an interrupt
        int tac = mmu.registers[R.R_TAC];
        if ((tac & 0b100) != 0)
        {
            timerCycle += delta;

            // The Timer has a settable frequency
            int timerPeriod = 0;

            /**
             * Bit 2    - Timer Stop  (0=Stop, 1=Start)
             * Bits 1-0 - Input Clock Select
             * 00:   4096 Hz    (~4194 Hz SGB)
             * 01: 262144 Hz  (~268400 Hz SGB)
             * 10:  65536 Hz   (~67110 Hz SGB)
             * 11:  16384 Hz   (~16780 Hz SGB)
             */
            switch (tac & 0b11)
            {
                case 0b00:
                    timerPeriod = clockSpeed / 4096;
                    break;
                case 0b01:
                    timerPeriod = clockSpeed / 262144;
                    break;
                case 0b10:
                    timerPeriod = clockSpeed / 65536;
                    break;
                case 0b11:
                    timerPeriod = clockSpeed / 16384;
                    break;
            }

            while (timerCycle >= timerPeriod)
            {
                //  System.out.println(timerPeriod);
                timerCycle -= timerPeriod;
                // And it resets to a specific value
                int tima = (mmu.registers[R.R_TIMA] & 0xff) + 1;
                if (tima > 0xff)
                {
                    // Reset to the wanted value, and trigger the interrupt
                    tima = mmu.registers[R.R_TMA] & 0xff;
                    //     if (isInterruptEnabled(R.TIMER_OVERFLOW_BIT))
                    setInterruptTriggered(R.TIMER_OVERFLOW_BIT);
//                        System.out.println("triggered TIMA");
                }
                mmu.registers[R.R_TIMA] = (byte) tima;
            }
        }

        sound.tick(delta);
        // Update the display
        lcd.tick(delta);
//        if((mmu.registers[R.R_LY]&0xff) == 144) {
//            debugger.vram.paintImmediately(debugger.vram.getBounds());
//        }
    }

    public long ac;
    public long executed;

    public void tick(long delta)
    {
        cycle += delta;
        ac += delta;
        executed += delta;

        updateInterrupts(delta);
    }

    public void exec()
    {
        long last = System.nanoTime();
        long _last = System.nanoTime();

        executeLock.acquireUninterruptibly();

        while (true)
        {
            tick(_exec());

            if (interruptsEnabled)
            {
                fireInterrupts();
            }

            if (System.nanoTime() - last > 1_000_000_000)
            {
                System.err.println(last + " -- " + clockSpeed + " Hz -- " + (1.0 * executed / clockSpeed));
                last = System.nanoTime();
                executed = 0;
            }
            // The idea here is that we cap the amount of cycles we execute per second to 4194304
            // it doesn't actually do this since some timing is off
            int t = 100000;///6000;//4194304;
            if (ac >= t)
            {
                // System.err.println(cycle + "..." + oldCycle);
                // 1 cycle takes 1/4194304 seconds
                // d cycles take d / 4194304 seconds, or (d / 4194304) * 1000 milliseconds
                // 1000000000 - (System.nanoTime() - last)
                executeLock.release();
                try
                {
                    //   LockSupport.parkNanos(1000000000 - (System.nanoTime() - _last));
                    if (emulateSpeed)
                    {
                        //sound.render(t);
                        LockSupport.parkNanos(1_000_000_000L * t / clockSpeed + _last - System.nanoTime());
                    } else
                    {
                        clockSpeed = (int) (1_000_000_000L * t / (System.nanoTime() - _last));
                        sound.updateClockSpeed(clockSpeed);
                        //sound.render(t);
                    }
                    _last = System.nanoTime();
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
                executeLock.acquireUninterruptibly();
                ac -= t;
            }
        }
    }

    public long cycle = 0;
    public long instr = 0;

    public int NOP(int op)
    {
        return 0;
    }


    public int CALL_cc_nn(int op)
    {
        int jmp = (nextUByte()) | (nextUByte() << 8);
        if (getConditionalFlag(0b100 | ((op >> 3) & 0x7)))
        {
            pushWord(pc);
            pc = jmp;
            return 4;
        }
        return 0;
    }

    public int CALL_nn(int op)
    {
        int jmp = (nextUByte()) | (nextUByte() << 8);
        pushWord(pc);
        pc = jmp;
        return 4;
    }

    public int LD_dd_nn(int op)
    {
        setRegisterPair(RegisterPair.byValue[(op >> 4) & 0x3], nextUByte() | (nextUByte() << 8));
        return 0;
    }

    public int LD_r_n(int op)
    {
        int to = (op >> 3) & 0x7;
        int n = nextUByte();
        setRegister(to, n);
        return 0;
    }

    public int LD_A_BC(int op)
    {
        A = getUByte(getRegisterPair(BC));
        return 0;
    }

    public int LD_A_DE(int op)
    {
        A = getUByte(getRegisterPair(DE));
        return 0;
    }

    public int LD_BC_A(int op)
    {
        setByte(getRegisterPair(BC), A);
        return 0;
    }

    public int LD_DE_A(int op)
    {
        setByte(getRegisterPair(DE), A);
        return 0;
    }

    public int LD_A_C(int op)
    {
        A = getUByte(0xFF00 | C);
        return 0;
    }

    public int ADD_SP_n(int op)
    {
        int offset = nextByte();
        int nsp = (SP + offset);

        F = 0;
        int carry = nsp ^ SP ^ offset;
        if ((carry & 0x100) != 0) F |= F_C;
        if ((carry & 0x10) != 0) F |= F_H;

        nsp &= 0xffff;

        SP = nsp;
        return 4;
    }

    public int SCF(int op)
    {
        F &= F_Z;
        F |= F_C;
        return 0;
    }

    public int CCF(int op)
    {
        F = (F & F_C) != 0 ? (F & F_Z) : ((F & F_Z) | F_C);
        return 0;
    }

    public int LD_A_n(int op)
    {
        A = getUByte(getRegisterPair(HL) & 0xffff);
        setRegisterPair(HL, (getRegisterPair(HL) - 1) & 0xFFFF);
        return 0;
    }

    public int LD_nn_A(int op)
    {
        setByte(nextUByte() | (nextUByte() << 8), A);
        return 0;
    }

    public int LDHL_SP_n(int op)
    {
        int offset = nextByte();
        //   offset = (short) ((offset & 0x7f) - (offset & 0x80));
        int nsp = (SP + offset);

        F = 0;//(short) (F & F_Z);
        int carry = nsp ^ SP ^ offset;
        if ((carry & 0x100) != 0) F |= F_C;
        if ((carry & 0x10) != 0) F |= F_H;
        nsp &= 0xffff;

        setRegisterPair(HL, nsp);
        return 0;
    }

    public int CPL(int op)
    {
        A = (~A) & 0xFF;
        F = (F & (F_C | F_Z)) | F_H | F_N;
        return 0;
    }

    public int LD_FFn_A(int op)
    {
        setByte(0xff00 | nextUByte(), A);
        return 0;
    }

    public int LDH_FFC_A(int op)
    {
        setByte(0xFF00 | (C & 0xFF), A);
        return 0;
    }

    public int LD_A_nn(int op)
    {
        int nn = nextUByte() | (nextUByte() << 8);
        A = getUByte(nn);
        return 0;
    }

    public int LD_A_HLI(int op)
    {
        A = getUByte(getRegisterPair(HL) & 0xffff);
        setRegisterPair(HL, (getRegisterPair(HL) + 1) & 0xFFFF);
        return 0;
    }

    public int LD_HLI_A(int op)
    {
        setByte(getRegisterPair(HL) & 0xFFFF, A);
        setRegisterPair(HL, (getRegisterPair(HL) + 1) & 0xFFFF);
        return 0;
    }

    public int LD_HLD_A(int op)
    {
        int hl = getRegisterPair(HL);
        setByte(hl, A);
        setRegisterPair(HL, (hl - 1) & 0xFFFF);

        return 0;
    }

    public int STOP(int op)
    {
        return NOP(op);
    }

    public static int[] opcount = new int[0x100];
    public static int curop = 0;

    public int _exec()
    {
        instr++;
        if (cpuHalted)
        {
            if (mmu.registers[R.R_TRIGGERED_INTERRUPTS] == 0)
                return 4;
            cpuHalted = false;
        }

        int op = nextUByte();

        opcount[op]++;
        curop++;

//        System.out.println(pc);

        outer:
        switch (op)
        {
            case 0x00:
                return NOP(op);
            case 0xC4:
                return CALL_cc_nn(op);
            case 0xCC:
                return CALL_cc_nn(op);
            case 0xD4:
                return CALL_cc_nn(op);
            case 0xDC:
                return CALL_cc_nn(op);
            case 0xCD:
                return CALL_nn(op);
            case 0x01:
                return LD_dd_nn(op);
            case 0x11:
                return LD_dd_nn(op);
            case 0x21:
                return LD_dd_nn(op);
            case 0x31:
                return LD_dd_nn(op);
            case 0x06:
                return LD_r_n(op);
            case 0x0E:
                return LD_r_n(op);
            case 0x16:
                return LD_r_n(op);
            case 0x1E:
                return LD_r_n(op);
            case 0x26:
                return LD_r_n(op);
            case 0x2E:
                return LD_r_n(op);
            case 0x36:
                return LD_r_n(op);
            case 0x3E:
                return LD_r_n(op);
            case 0x0A:
                return LD_A_BC(op);
            case 0x1A:
                return LD_A_DE(op);
            case 0x02:
                return LD_BC_A(op);
            case 0x12:
                return LD_DE_A(op);
            case 0xF2:
                return LD_A_C(op);
            case 0xE8:
                return ADD_SP_n(op);
            case 0x37:
                return SCF(op);
            case 0x3F:
                return CCF(op);
            case 0x3A:
                return LD_A_n(op);
            case 0xEA:
                return LD_nn_A(op);
            case 0xF8:
                return LDHL_SP_n(op);
            case 0x2F:
                return CPL(op);
            case 0xE0:
                return LD_FFn_A(op);
            case 0xE2:
                return LDH_FFC_A(op);
            case 0xFA:
                return LD_A_nn(op);
            case 0x2A:
                return LD_A_HLI(op);
            case 0x22:
                return LD_HLI_A(op);
            case 0x32:
                return LD_HLD_A(op);
            case 0x10:
                return STOP(op);
            // LD SP, HL
            case 0xf9:
            {
                setRegisterPair(RegisterPair.SP, getRegisterPair(HL));
                break;
            }
            // PUSH qq
            case 0xc5: // BC
            case 0xd5: // DE
            case 0xe5: // HL
            case 0xf5: // AF
            {
                return PUSH_rr(op);
            }
            // POP qq
            case 0xc1: // BC
            case 0xd1: // DE
            case 0xe1: // HL
            case 0xf1: // AF
            {

                // System.out.println(">>>" + SP);
                return POP_rr(op);
            }
            // LD (a16), SP
            case 0x08:
            {
                return LD_a16_SP();
            }
            // RETI (EXX on z80)
            case 0xd9:
            {
                return RETI();
            }
            // JP nn
            case 0xc3:
            {
                return JP_nn();
            }
            // RLCA
            case 0x07:
            {
                RLCA();
                break;
            }
            // INC r
            case 0x3c: // A
            case 0x4: // B
            case 0xc: // C
            case 0x14: // D
            case 0x1c: // E
            case 0x24: // F
            case 0x34: // (HL)
            case 0x2c: // G
            {
                INC_r(op);
                break;
            }
            // DEC r
            case 0x3d: // A
            case 0x05: // B
            case 0x0d: // C
            case 0x15: // D
            case 0x1d: // E
            case 0x25: // H
            case 0x2d: // L
            case 0x35: // (HL)
            {
                DEC_r(op);
                break;
            }
            // INC ss
            case 0x3:
            case 0x13:
            case 0x23:
            case 0x33:
            {
                INC_rr(op);
                break;
            }
            // CP rr
            case 0xb8:
            case 0xb9:
            case 0xba:
            case 0xbb:
            case 0xbc:
            case 0xbd:
            case 0xbe:
            case 0xbf:
            {
                CP_rr(op);
                break;
            }
            // CP s
            case 0xfe:
            {
                CP_n();
                break;
            }
            // ADD HL, ss
            case 0x09:
            case 0x19:
            case 0x29:
            case 0x39:
            {
                ADD_HL_rr(op);
                break;
            }
            // JP (HL)
            case 0xe9:
            {
                JP_HL();
                break;
            }
            // SBC A, n
            case 0xde:
            {
                SBC_n();
                break;

            }
            // SUB A, n
            case 0xd6:
            {
                SUB_n();
                break;
            }
            // SUB A, r
            case 0x90:
            case 0x91:
            case 0x92:
            case 0x93:
            case 0x94:
            case 0x95:
            case 0x96: // (HL)
            case 0x97:
            {
                SUB_r(op);
                break;
            }
            // ADD A, n
            case 0xc6:
            {
                ADD_n();
                break;
            }
            // ADD A, r
            case 0x87:
            case 0x80:
            case 0x81:
            case 0x82:
            case 0x83:
            case 0x84:
            case 0x85:
            case 0x86: // (HL)
            {
                ADD_r(op);
                break;
            }
            // ADC A, s
            case 0x88:
            case 0x89:
            case 0x8a:
            case 0x8b:
            case 0x8c:
            case 0x8e:
            case 0x8d:
            case 0x8f:
            {
                ADC_r(op);
                break;
            }
            // AND s
            case 0xa0:
            case 0xa1:
            case 0xa2:
            case 0xa3:
            case 0xa4:
            case 0xa5:
            case 0xa6: // (HL)
            case 0xa7:
            {
                AND_r(op);
                break;
            }

            // XOR s
            case 0xa8:
            case 0xa9:
            case 0xaa:
            case 0xab:
            case 0xac:
            case 0xad:
            case 0xae:
            case 0xaf:
                XOR_r(op);
                break;
            // OR n
            case 0xf6:
                OR_n();
                break;
            // OR A, s
            case 0xb0:
            case 0xb1:
            case 0xb2:
            case 0xb3:
            case 0xb4:
            case 0xb5:
            case 0xb6: // (HL)
            case 0xb7:
                OR_r(op);
                break;
            // JR e
            case 0x18:
                return JR_e();
            // DAA
            case 0x27:
                DAA();
                break;
            // JP C, nn
            case 0xca:
            case 0xc2: // NZ
            case 0xd2:
            case 0xda:
                return JP_c_nn(op);
            // JR C, e
            case 0x20: // NZ
            case 0x28:
            case 0x30:
            case 0x38:
                return JR_c_e(op);
            // LDH A, (FFnn)
            case 0xf0:
                LDH_FFnn();
                break;
            // HALT
            case 0x76:
                return HALT();
            // RET cc
            case 0xc0: // NZ non zero (Z)
            case 0xc8: // Z zero (Z)
            case 0xd0: // NC non carry (C)
            case 0xd8: // Carry (C)
                return RET_c(op);
            // RST p
            case 0xc7:
            case 0xcf:
            case 0xd7:
            case 0xdf:
            case 0xe7:
            case 0xef:
            case 0xf7:
            case 0xff:
                return RST_p(op);
            // DI
            case 0xf3:
                DI();
                break;
            // EI
            case 0xfb:
                return EI();
            // AND nn
            case 0xE6:
                AND_n();
                break;
            // XOR nn
            case 0xEE:
                XOR_n();
                break;
            // RET
            case 0xc9:
                return RET();
            // ADC A, n
            case 0xce:
                ADC_n();
                break;
            // SBC A, s
            case 0x98:
            case 0x99:
            case 0x9a:
            case 0x9b:
            case 0x9c:
            case 0x9d:
            case 0x9e: // (HL)
            case 0x9f:
                SBC_r(op);
                break;
            case 0x0F: // RRCA
                RRCA();
                break;
            case 0x1f: // RRA
                RRA();
                break;
            case 0x17: // RLA
                RLA();
                break;
            // DEC ss
            case 0x0b:
            case 0x1b:
            case 0x2b:
            case 0x3b:
                DEC_rr(op);
                break;
            // CB prefix group
            case 0xcb:
                CBPrefix();
                break;
            default:
                switch (op & 0xC0)
                {
                    case 0x40: // LD r, r'
                        LD_r_r(op);
                        break;
                    default:
                        throw new UnsupportedOperationException(cycle + "-" + Integer.toHexString(op));
                }
        }
        return 0;
    }

    private void LD_r_r(int op)
    {
        // 0 1 r r r r' r' r'
        int from = op & 0x7;
        int to = (op >> 3) & 0x7;
        // important note: getIO(6) fetches (HL)
        setRegister(to, getRegister(from) & 0xFF);
    }

    private void CBPrefix()
    {
        int x = pc++;

        int cbop = getUByte(x);
        int r = cbop & 0x7;
        int d = getRegister(r) & 0xff;

        switch ((cbop & 0b11000000))
        {
            // RES b, r
            // 1 0 b b b r r r
            case 0x80:
            {
                setRegister(r, d & ~(0x1 << (cbop >> 3 & 0x7)));
                return;
            }
            // SET b, r
            // 1 1 b b b r r r
            case 0xc0:
            {
                setRegister(r, d | (0x1 << (cbop >> 3 & 0x7)));
                return;
            }
            // BIT b, r
            // 0 1 b b b r r r
            case 0x40:
            {
                F &= F_C;
                F |= F_H;
                if ((d & (0x1 << (cbop >> 3 & 0x7))) == 0) F |= F_Z;
                return;
            }
            case 0x0:
            {
                switch (cbop & 0xf8)
                {
                    case 0x00: // RLC m
                    {
                        F = 0;
                        if ((d & 0x80) != 0) F |= F_C;
                        d <<= 1;
                        // we're shifting circular left, add back bit 7
                        if ((F & F_C) != 0) d |= 0x01;
                        d &= 0xff;
                        if (d == 0) F |= F_Z;
                        setRegister(r, d);
                        return;
                    }
                    case 0x08: // RRC m
                    {
                        F = 0;
                        if ((d & 0b1) != 0) F |= F_C;
                        d >>= 1;
                        // we're shifting circular right, add back bit 7
                        if ((F & F_C) != 0) d |= 0x80;
                        d &= 0xff;
                        if (d == 0) F |= F_Z;
                        setRegister(r, d);
                        return;
                    }
                    case 0x10: // RL m
                    {
                        boolean carryflag = (F & F_C) != 0;
                        F = 0;
                        // we'll be shifting left, so if bit 7 is set we set carry
                        if ((d & 0x80) == 0x80) F |= F_C;
                        d <<= 1;
                        d &= 0xff;
                        // move old C into bit 0
                        if (carryflag) d |= 0b1;
                        if (d == 0) F |= F_Z;
                        setRegister(r, d);
                        return;
                    }
                    case 0x18: // RR m
                    {
                        boolean carryflag = (F & F_C) != 0;
                        F = 0;
                        // we'll be shifting right, so if bit 1 is set we set carry
                        if ((d & 0x1) == 0x1) F |= F_C;
                        d >>= 1;
                        // move old C into bit 7
                        if (carryflag) d |= 0b10000000;
                        if (d == 0) F |= F_Z;
                        setRegister(r, d);
                        return;
                    }
                    case 0x38: // SRL m
                    {
                        F = 0;
                        // we'll be shifting right, so if bit 1 is set we set carry
                        if ((d & 0x1) != 0) F |= F_C;
                        d >>= 1;
                        if (d == 0) F |= F_Z;
                        setRegister(r, d);
                        return;
                    }
                    case 0x20: // SLA m
                    {
                        F = 0;
                        // we'll be shifting right, so if bit 1 is set we set carry
                        if ((d & 0x80) != 0) F |= F_C;
                        d <<= 1;
                        d &= 0xff;
                        if (d == 0) F |= F_Z;
                        setRegister(r, d);
                        return;
                    }
                    case 0x28: // SRA m
                    {
                        boolean bit7 = (d & 0x80) != 0;
                        F = 0;
                        if ((d & 0b1) != 0) F |= F_C;
                        d >>= 1;
                        if (bit7) d |= 0x80;
                        if (d == 0) F |= F_Z;
                        setRegister(r, d);
                        return;
                    }
                    case 0x30: // SWAP m
                    {
                        d = ((d & 0xF0) >> 4) | ((d & 0x0F) << 4);
                        F = d == 0 ? F_Z : 0;
                        setRegister(r, d);
                        return;
                    }
                    default:
                        throw new UnsupportedOperationException("cb-&f8-" + Integer.toHexString(cbop));
                }
            }
            default:
                throw new UnsupportedOperationException("cb-" + Integer.toHexString(cbop));
        }
    }

    private void DEC_rr(int op)
    {
        RegisterPair p = RegisterPair.byValue[(op >> 4) & 0x3];
        int o = getRegisterPair(p);
        setRegisterPair(p, o - 1);
        doOamBug(o);
    }

    private void RLA()
    {
        boolean carryflag = (F & F_C) != 0;
        F = 0;//&= F_Z;
        // we'll be shifting left, so if bit 7 is set we set carry
        if ((A & 0x80) == 0x80) F |= F_C;
        A <<= 1;
        A &= 0xff;
        // move old C into bit 0
        if (carryflag) A |= 1;
    }

    private void RRA()
    {
        boolean carryflag = (F & F_C) != 0;
        F = 0;
        // we'll be shifting right, so if bit 1 is set we set carry
        if ((A & 0x1) == 0x1) F |= F_C;
        A >>= 1;
        // move old C into bit 7
        if (carryflag) A |= 0x80;
    }

    private void RRCA()
    {
        F = 0;//F_Z;
        if ((A & 0x1) == 0x1) F |= F_C;
        A >>= 1;
        // we're shifting circular right, add back bit 7
        if ((F & F_C) != 0) A |= 0x80;
    }

    private void SBC_r(int op)
    {
        int carry = (F & F_C) != 0 ? 1 : 0;
        int reg = getRegister(op & 0b111) & 0xff;

        F = F_N;
        if ((A & 0x0f) - (reg & 0x0f) - carry < 0) F |= F_H;
        A -= reg + carry;
        if (A < 0)
        {
            F |= F_C;
            A &= 0xFF;
        }
        if (A == 0) F |= F_Z;
    }

    private void ADC_n()
    {
        int val = nextUByte();
        int carry = ((F & F_C) != 0 ? 1 : 0);
        int n = val + carry;

        F = 0;
        if ((((A & 0xf) + (val & 0xf)) + carry & 0xF0) != 0) F |= F_H;
        A += n;
        if (A > 0xFF)
        {
            F |= F_C;
            A &= 0xFF;
        }
        if (A == 0) F |= F_Z;
    }

    private int RET()
    {
        pc = (getUByte(SP + 1) << 8) | getUByte(SP);
        SP += 2;
        return 4;
    }

    private void XOR_n()
    {
        A ^= nextUByte();
        F = 0;
        if (A == 0) F |= F_Z;
    }

    private void AND_n()
    {
        A &= nextUByte();
        F = F_H;
        if (A == 0) F |= F_Z;
    }

    private int EI()
    {
        interruptsEnabled = true;
        // Note that during the execution of this instruction and the following instruction,
        // maskable interrupts are disabled.

        // we still need to increment div etc
        tick(4);
        return _exec();
    }

    private void DI()
    {
        //   System.err.println("Disabled interrupts");
        interruptsEnabled = false;
    }

    private int RST_p(int op)
    {
    /*

    short hi = (short) ((val >> 8) & 0xFF);
    short lo = (short) (val & 0xFF);
    setMemory(SP - 2, lo);
    setMemory(SP - 1, hi);
     */
        pushWord(pc);
        pc = op & 0b00111000;
        return 4;
    }

    private int RET_c(int op)
    {
        if (getConditionalFlag(0b100 | ((op >> 3) & 0x7)))
        {
            //pc = ((memory[SP] & 0xff) << 8) | (memory[SP+1] & 0xff);
            pc = (getUByte(SP + 1) << 8) | getUByte(SP);
            SP += 2;
        }
        return 4;
    }

    private int HALT()
    {
        cpuHalted = true;
        return 0;
    }

    private void LDH_FFnn()
    {
        A = getUByte(0xFF00 | nextUByte());
    }

    private int JR_c_e(int op)
    {
        int e = nextByte();
        if (getConditionalFlag((op >> 3) & 0b111))
        {
            //     System.out.printf("> branching to %04X (%d)\n", (pc + e), e);
            pc += e;
            return 4;
        }
        return 0;
    }

    private int JP_c_nn(int op)
    {
        int npc = nextUByte() | (nextUByte() << 8);
        if (getConditionalFlag(0b100 | ((op >> 3) & 0x7)))
        {
            pc = npc;
            return 4;
        }
        return 0;
    }

    private void DAA()
    {
        // TODO warning: this might be implemented wrong!
        /**
         * <code><pre>tmp := a,
         * if nf then
         *      if hf or [a AND 0x0f > 9] then tmp -= 0x06
         *      if cf or [a > 0x99] then tmp -= 0x60
         * else
         *      if hf or [a AND 0x0f > 9] then tmp += 0x06
         *      if cf or [a > 0x99] then tmp += 0x60
         * endif,
         * tmp => flags, cf := cf OR [a > 0x99],
         * hf := a.4 XOR tmp.4, a := tmp
         * </pre>
         * </code>
         */
//                short tmp = A;
//                if ((F & F_N) != 0)
//                {
//                    if ((F & F_H) != 0 || (A & 0x0f) > 9) tmp -= 0x06;
//                    if ((F & F_C) != 0 || A > 0x99) tmp -= 0x60;
//                } else
//                {
//                    if ((F & F_H) != 0 || (A & 0x0f) > 9) tmp += 0x06;
//                    if ((F & F_C) != 0 || A > 0x99) tmp += 0x60;
//                }
//                F = (short) (tmp & (F_C | F_H | F_N | F_Z));
//                //    F = (short) (tmp & (F_N | F_Z | F_C | F_H));
//                if (A > 0x99) F |= F_C;
//                if (((A & 0b10000) ^ (tmp & 0b10000)) != 0) F |= F_H;
//
//                if (tmp == 0) F |= F_Z;
//
//                A = tmp;
        int tmp = A;
        if ((F & F_N) == 0)
        {
            if ((F & F_H) != 0 || ((tmp & 0x0f) > 9)) tmp += 0x06;
            if ((F & F_C) != 0 || ((tmp > 0x9f))) tmp += 0x60;
        } else
        {
            if ((F & F_H) != 0) tmp = ((tmp - 6) & 0xff);
            if ((F & F_C) != 0) tmp -= 0x60;
        }
        F &= F_N | F_C;

        if (tmp > 0xff)
        {
            F |= F_C;
            tmp &= 0xff;
        }

        if (tmp == 0) F |= F_Z;

        A = tmp;
    }

    private int JR_e()
    {
        int e = nextByte();
        //  System.out.printf("> branching to %04X\n", (pc + e));

        pc += e;
        return 4;
    }

    private void OR(int n)
    {
        A |= n;
        F = 0;
        if (A == 0) F |= F_Z;
    }

    private void OR_r(int op)
    {
        OR(getRegister(op & 0b111) & 0xff);
    }

    private void OR_n()
    {
        int n = nextUByte();
        OR(n);
    }

    private void XOR_r(int op)
    {
        A = (A ^ getRegister(op & 0b111)) & 0xff;
        F = 0;
        if (A == 0) F |= F_Z;
    }

    private void AND_r(int op)
    {
        A = (A & getRegister(op & 0b111)) & 0xff;
        F = F_H;
        if (A == 0) F |= F_Z;
    }

    private void ADC_r(int op)
    {
        int carry = ((F & F_C) != 0 ? 1 : 0);
        int reg = (getRegister(op & 0b111) & 0xff);

        int d = carry + reg;
        F = 0;
        if ((((A & 0xf) + (reg & 0xf) + carry) & 0xF0) != 0) F |= F_H;

        A += d;
        if (A > 0xFF)
        {
            F |= F_C;
            A &= 0xFF;
        }
        if (A == 0) F |= F_Z;
    }

    private void ADD(int n)
    {
        F = 0;
        if ((((A & 0xf) + (n & 0xf)) & 0xF0) != 0) F |= F_H;
        A += n;
        if (A > 0xFF)
        {
            F |= F_C;
            A &= 0xFF;
        }
        if (A == 0) F |= F_Z;

    }

    private void ADD_r(int op)
    {
        int n = getRegister(op & 0b111) & 0xff;
        ADD(n);
    }

    private void ADD_n()
    {
        int n = nextUByte();
        ADD(n);
    }

    private void SUB(int n)
    {
        F = F_N;
        if ((A & 0xf) - (n & 0xf) < 0) F |= F_H;
        A -= n;
        if ((A & 0xFF00) != 0) F |= F_C;
        A &= 0xFF;
        if (A == 0) F |= F_Z;
    }

    private void SUB_r(int op)
    {
        int n = getRegister(op & 0b111) & 0xff;
        SUB(n);
    }

    private void SUB_n()
    {
        int n = nextUByte();
        SUB(n);
    }

    private void SBC_n()
    {
        //                short d = (short) (nextUByte() + ((F & F_C) != 0 ? 1 : 0));
//                F = 0;
//                if ((A & 0xf) - (d & 0xf) < 0) F |= F_H;
//                A -= d;
//                if ((A & 0xFF00) != 0) F |= F_C;
//                A &= 0xFF;
//                if (A == 0) F |= F_Z;

        int val = nextUByte();
        int carry = ((F & F_C) != 0 ? 1 : 0);
        int n = val + carry;

        F = F_N;
        if ((A & 0xf) - (val & 0xf) - carry < 0) F |= F_H;
        A -= n;
        if (A < 0)
        {
            F |= F_C;
            A &= 0xff;
        }
        if (A == 0) F |= F_Z;

    }

    private void JP_HL()
    {
        pc = getRegisterPair(HL) & 0xFFFF;
    }

    private void ADD_HL_rr(int op)
    {
        /**
         * Z is not affected
         * H is set if carry out of bit 11; reset otherwise
         * N is reset
         * C is set if carry from bit 15; reset otherwise
         */
        int ss = getRegisterPair(RegisterPair.byValue[(op >> 4) & 0x3]);
        int hl = getRegisterPair(HL);

        F &= F_Z;

        if (((hl & 0xFFF) + (ss & 0xFFF)) > 0xFFF)
        {
            F |= F_H;
        }

        hl += ss;

        if (hl > 0xFFFF)
        {
            F |= F_C;
            hl &= 0xFFFF;
        }

        setRegisterPair(HL, hl);
    }

    private void CP(int n)
    {
        F = F_N;
        if (A < n) F |= F_C;
        if (A == n) F |= F_Z;
        if ((A & 0xf) < ((A - n) & 0xf)) F |= F_H;
    }

    private void CP_n()
    {
        int n = nextUByte();
        CP(n);
    }

    private void CP_rr(int op)
    {
        int n = getRegister(op & 0x7) & 0xFF;
        CP(n);
    }

    private void INC_rr(int op)
    {
        RegisterPair pair = RegisterPair.byValue[(op >> 4) & 0x3];
        int o = getRegisterPair(pair) & 0xffff;
        setRegisterPair(pair, o + 1);
        doOamBug(o);
    }

    private void DEC_r(int op)
    {
        int reg = (op >> 3) & 0x7;
        int a = getRegister(reg) & 0xff;

        F = (F & F_C) | Tables.DEC[a];

        a = (a - 1) & 0xff;

        setRegister(reg, a);
    }

    private void INC_r(int op)
    {
        int reg = (op >> 3) & 0x7;
        int a = getRegister(reg) & 0xff;

        F = (F & F_C) | Tables.INC[a];

        a = (a + 1) & 0xff;

        setRegister(reg, a);
    }

    private void RLCA()
    {
        boolean carry = (A & 0x80) != 0;
        A <<= 1;
        F = 0;//&= F_Z;
        if (carry)
        {
            F |= F_C;
            A |= 1;
        } else F = 0;
        A &= 0xff;
    }

    private int JP_nn()
    {
        // n n n n n n n n
        // n n n n n n n n
        pc = (nextUByte()) | (nextUByte() << 8);
        // System.out.printf("> branching to %04X\n", pc);
        return 4;
    }

    private int RETI()
    {
        interruptsEnabled = true;
        pc = (getUByte(SP + 1) << 8) | getUByte(SP);
        SP += 2;
        return 4;
    }

    private int LD_a16_SP()
    {
        int pos = ((nextUByte()) | (nextUByte() << 8));
        setByte(pos + 1, (SP & 0xFF00) >> 8);
        setByte(pos, (SP & 0x00FF));
        return 0;
    }

    private int POP_rr(int op)
    {
        setRegisterPair2(RegisterPair.byValue[(op >> 4) & 0x3], getByte(SP + 1), getByte(SP));
//                if(op == 0xc1)
//                    System.err.println("POPPED " + Integer.toHexString(getRegisterPair2(P_BC) & 0xFFFF));
//                System.err.println("POPPED " + Integer.toHexString(getRegisterPair2((op >> 4) & 0x3) & 0xFFFF));
        SP += 2;
        return 0;
    }

    private int PUSH_rr(int op)
    {
        int val = getRegisterPair2(RegisterPair.byValue[(op >> 4) & 0x3]);
        pushWord(val);
        return 4;
    }

    private void doOamBug(int o)
    {
        // TODO
//        if (o == 0xff00 || o == 0xff04) return;
//        if (0xfe00 <= o && o <= 0xfeff)
//        {
//            for (int i = 0; i < mmu.oam.length; i++)
//            {
//                if (i == 0x00 || i == 0x04) continue;
//                mmu.oam[i] = (byte) (Math.random() * 255);
//            }
//        }
    }


    public static final BufferedImage screenBuffer = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
    public static Panel display;
    //public static VRAMViewer debugger;

    public static void main(String[] argv) throws IOException, ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException
    {
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        System.setProperty("sun.java2d.opengl", "false");
//        System.setProperty("sun.java2d.xrender", "false");
        System.setProperty("sun.java2d.d3d", "true");

//        JFrame x = new JFrame("AAA");
//        x.add(new JButton("Cliq me") {
//            {
//                setPreferredSize(new Dimension(100, 100));
//            }
//        });
//        x.pack();
//        x.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        x.setLocationRelativeTo(null);
//        x.setVisible(true);

//        if(true) return;

        //    System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream("emu.log"))));

        File f = new File(argv[0]);
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        // TODO fix
        in.read(buf);
        in.close();


        Cartridge cartridge = new Cartridge(buf);

        Emulator core = new Emulator(cartridge);

        //    debugger = new VRAMViewer(core);

        File savefile = new File(cartridge.gameTitle + ".sav");

        if (core.mmu.hasBattery())
        {
            try
            {
                core.mmu.load(new FileInputStream(savefile));
            } catch (Exception ignored)
            {

            }
        }

        Thread codeExecutionThread = new Thread(core::exec);

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> {
            JFrame disp = new JFrame(cartridge.gameTitle);
            disp.setContentPane(new Panel()
            {
                {
                    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                    add(Box.createVerticalGlue());
                    setBackground(Color.BLACK);
                    setIgnoreRepaint(true);

                    add(new Panel()
                    {
                        {
                            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                            setBackground(Color.BLACK);
                            setIgnoreRepaint(true);

                            add(Box.createHorizontalGlue());
                            add(display = new Panel()
                            {
                                {
                                    int mag = 2;
                                    setBackground(Color.BLACK);
                                    setMaximumSize(new Dimension(160 * mag, 144 * mag));
                                    setMinimumSize(new Dimension(160 * mag, 144 * mag));
                                    setSize(new Dimension(160 * mag, 144 * mag));
                                    setPreferredSize(new Dimension(160 * mag, 144 * mag));
//                    setBackground(Color.GREEN);
                                    KeyListener toggler = new KeyAdapter()
                                    {
                                        private void toggle(KeyEvent e, boolean to)
                                        {
                                            switch (e.getKeyCode())
                                            {
                                                case KeyEvent.VK_RIGHT:
                                                    core.buttonRight = to;
                                                    break;
                                                case KeyEvent.VK_LEFT:
                                                    core.buttonLeft = to;
                                                    break;
                                                case KeyEvent.VK_UP:
                                                    core.buttonUp = to;
                                                    break;
                                                case KeyEvent.VK_DOWN:
                                                    core.buttonDown = to;
                                                    break;
                                                case KeyEvent.VK_A:
                                                    core.buttonA = to;
                                                    break;
                                                case KeyEvent.VK_B:
                                                    core.buttonB = to;
                                                    break;
                                                case KeyEvent.VK_X:
                                                    core.buttonStart = to;
                                                    break;
                                                case KeyEvent.VK_Y:
                                                    core.buttonSelect = to;
                                                    break;
                                            }
                                        }

                                        @Override
                                        public void keyReleased(KeyEvent e)
                                        {
                                            toggle(e, false);
                                        }

                                        @Override
                                        public void keyPressed(KeyEvent e)
                                        {
                                            toggle(e, true);
                                        }
                                    };
                                    addMouseListener(new MouseAdapter()
                                    {
                                        @Override
                                        public void mouseReleased(MouseEvent e)
                                        {
                                            JPopupMenu menu = new JPopupMenu();
                                            menu.add(new JMenu("Renderer")
                                            {
                                                {
                                                    ButtonGroup group = new ButtonGroup();

                                                    for (IRenderManager renderer : core.lcd.renderers)
                                                    {
                                                        JRadioButtonMenuItem menuItem = renderer.getRadioMenuItem(core.lcd);
                                                        group.add(menuItem);
                                                        if (renderer == core.lcd.currentRenderer)
                                                            group.setSelected(menuItem.getModel(), true);
                                                        add(menuItem);
                                                    }
                                                }
                                            });
                                            menu.add(new JMenu("Filter")
                                            {
                                                {
                                                    ButtonGroup group = new ButtonGroup();
                                                    for (final Interpolator interpolator : Interpolator.values())
                                                    {
                                                        add(new JRadioButtonMenuItem(interpolator.name)
                                                        {
                                                            {
                                                                group.add(this);
                                                                if (interpolator == core.lcd.interpolator)
                                                                    group.setSelected(getModel(), true);
                                                                addActionListener((e) -> {
                                                                    core.lcd.interpolator = interpolator;
                                                                    group.setSelected(getModel(), true);
                                                                });
                                                            }
                                                        });
                                                    }
                                                }
                                            });
                                            menu.add(new JMenu("Sound")
                                            {
                                                {
                                                    for (int i = 1; i < 5; i++)
                                                    {
                                                        int channel = i;
                                                        add(new JCheckBoxMenuItem("Channel " + i, core.sound.isChannelEnabled(channel))
                                                        {
                                                            {
                                                                addActionListener((x) ->
                                                                {
                                                                    core.sound.setChannelEnabled(channel, !core.sound.isChannelEnabled(channel));
                                                                });
                                                            }
                                                        });
                                                    }
                                                }
                                            });
                                            menu.add(new JSeparator());
                                            menu.add(new JCheckBoxMenuItem("Pause", core.paused)
                                            {
                                                {
                                                    addActionListener((x) ->
                                                    {
                                                        if (core.paused)
                                                            core.executeLock.release();
                                                        else
                                                            core.executeLock.acquireUninterruptibly();
                                                        core.paused = !core.paused;
                                                    });
                                                }
                                            });
                                            menu.add(new JCheckBoxMenuItem("Mute", core.sound.muted)
                                            {
                                                {
                                                    addActionListener((x) ->
                                                    {
                                                        core.sound.muted = !core.sound.muted;
                                                    });
                                                }
                                            });
                                            menu.add(new JMenu("Speed")
                                            {
                                                {
                                                    ButtonGroup group = new ButtonGroup();
                                                    for (final EmulateSpeed speed : EmulateSpeed.values())
                                                    {
                                                        add(new JRadioButtonMenuItem(speed.name)
                                                        {
                                                            {
                                                                group.add(this);
                                                                if (core.clockSpeed == speed.clockSpeed)
                                                                    group.setSelected(getModel(), true);
                                                                addActionListener(e -> {
                                                                    core.clockSpeed = speed.clockSpeed;
                                                                    core.sound.updateClockSpeed(speed.clockSpeed);
                                                                    group.setSelected(getModel(), true);
                                                                });
                                                            }
                                                        });
                                                    }
                                                }
                                            });

                                            menu.add(new JSeparator());
                                            menu.add(new JMenuItem("Options"));

                                            if (SwingUtilities.isRightMouseButton(e))
                                                menu.show(e.getComponent(), e.getX(), e.getY());
                                        }
                                    });
                                    addKeyListener(toggler);
                                    disp.addKeyListener(toggler);
                                    setIgnoreRepaint(true);
                                }

                                @Override
                                public void paint(Graphics g)
                                {
                                    throw new RuntimeException();
                                    //System.err.println("a");
                                    //super.paintComponent(g);
//                       ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                    //   g.drawImage(screenBuffer, 0, 0, getWidth(), getHeight(), null);
                                }
                            });
                            add(Box.createHorizontalGlue());
                        }
                    });

                    add(Box.createVerticalGlue());
                }
            });


            boolean fullscreen = false;
            if (fullscreen)
            {
                disp.setUndecorated(true);
                disp.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
            disp.pack();
            disp.setResizable(false);
            disp.setLocationRelativeTo(null);
            disp.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            disp.addWindowListener(new WindowAdapter()
            {
                @Override
                public void windowClosing(WindowEvent evt)
                {
                    System.err.println(core.cycle);
                    try
                    {
                        FileOutputStream f = new FileOutputStream(savefile);
                        if (core.mmu.hasBattery())
                        {
                            System.err.println("Saving cart ram");
                            core.mmu.save(f);
                        }
                        f.close();
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            });

            disp.setVisible(true);
//            disp.setIgnoreRepaint(true);


            core.lcd.initializeRenderers();
            //          core.lcd.currentRenderer.getGraphics().getDeviceConfiguration().getDevice().setFullScreenWindow(disp);

            codeExecutionThread.start();
        });

        System.err.println(cartridge.gameTitle);
        System.err.println(cartridge);
        System.out.flush();
    }

    public void pushWord(int what)
    {
        SP -= 2;

        setByte(SP, what & 0x00FF);
        setByte(SP + 1, (what & 0xFF00) >> 8);
    }

    public int nextUByte()
    {
        return getUByte(pc++);
    }

    public int nextByte()
    {
        return getByte(pc++);
    }

    public void setByte(int addr, int _data)
    {
        tick(4);
        mmu.setAddress(addr, _data);
    }

    public void setIO(int addr, int data)
    {
        tick(4);
        mmu.setIO(addr, data);
    }

    public int getUByte(int addr)
    {
        return getByte(addr) & 0xff;
    }

    public int getByte(int addr)
    {
        tick(4);
        return mmu.getAddress(addr);
    }

    public int getIO(int addr)
    {
        tick(4);
        return mmu.getIO(addr);
    }
}
