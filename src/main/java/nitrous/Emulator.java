package nitrous;

import nitrous.lcd.LCD;
import nitrous.mbc.Memory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.locks.LockSupport;

import static nitrous.Emulator.RegisterPair.*;

public class Emulator
{
    public Emulator(Cartridge cartridge)
    {
        this.cartridge = cartridge;
        this.mmu = cartridge.createController(this);
        this.lcd = new LCD(this);
        reset();
    }

    public boolean timerEnabled;
    public int timerFreq;

    public Memory mmu;
    public LCD lcd;

    public Cartridge cartridge;

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

        public static RegisterPair[] byValue = {BC, DE, HL, SP};
    }

    /**
     * This is... obvious?
     */
    public boolean buttonRight, buttonLeft, buttonStart, buttonSelect, buttonUp, buttonDown, buttonA, buttonB;

    public Op[] instrs = new Op[0x100];

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
                F = (short) (lo & (F_C | F_H | F_N | F_Z));
                break;
        }
    }

    public void reset()
    {
        // On startup, a CGB has 11h in A, a normal GB has 01h
        A = (short) (cartridge.isColorGB ? 0x11 : 0x01);
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
    public boolean emulateSpeed = false;

    public long updateInterrupts(long cycles)
    {
        // The DIV register increments at 16KHz, and resets to 0 after
        divCycle += cycle;
        if (divCycle >= 16384)
        {
            divCycle -= 16384;
            // This is... probably correct
            mmu.registers[R.R_DIV]++;
        }

        // The Timer is similar to DIV, except that when it overflows it triggers an interrupt
        if (timerEnabled)
            timerCycle += cycle;

        // The Timer has a settable frequency
        if (timerCycle >= timerFreq)
        {
            timerCycle -= timerFreq;

            // And it resets to a specific value
            int tima = (getIO(R.R_TIMA) & 0xff) + 1;
            if (tima > 0xff)
            {
                // Reset to the wanted value, and trigger the interrupt
                tima = getIO(R.R_TMA) & 0xff;
                if (isInterruptEnabled(R.TIMER_OVERFLOW_BIT))
                    setInterruptTriggered(R.TIMER_OVERFLOW_BIT);
            }
            setIO(R.R_TIMA, tima);
        }

        // Update the display
        lcd.tick(cycles);
        return 0;
    }

    public long ac;
    public long executed;

    public void exec()
    {
        long last = System.nanoTime();

        while (true)
        {
            long delta = _exec();
            cycle += delta;
            ac += delta;
            executed += delta;

            updateInterrupts(delta);

            if (interruptsEnabled)
            {
                fireInterrupts();
            }

            if (System.nanoTime() - last > 1_000_000_000)
            {
                System.err.println(last + " -- " + (executed / 4194304.0));
                last = System.nanoTime();
                executed = 0;
            }
            // The idea here is that we cap the amount of cycles we execute per second to 4194304
            // it doesn't actually do this since some timing is off
            int t = 6000;//4194304;
            if (ac >= t)
            {
                // System.err.println(cycle + "..." + oldCycle);
                // 1 cycle takes 1/4194304 seconds
                // d cycles take d / 4194304 seconds, or (d / 4194304) * 1000 milliseconds
                // 1000000000 - (System.nanoTime() - last)
                try
                {
                    //   LockSupport.parkNanos(1000000000 - (System.nanoTime() - _last));
                    // _last = System.nanoTime();
                    if (emulateSpeed) LockSupport.parkNanos((long) ((t / 4194304.0) * 1_000_000_000));
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
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
        //   offset = (short) ((offset & 0x7f) - (offset & 0x80));
        int nsp = (SP + offset);

        F = 0;//(short) (F & F_Z);

        if (offset >= 0)
        {
            if ((SP & 0xff) + offset > 0xff) F |= F_C;
            if ((((SP & 0xf) + (offset & 0xf)) & 0xF0) != 0) F |= F_H;
        } else
        {
//                                if ((nsp & 0xff) <= (SP & 0xff)) F |= F_C;
//                                if ((nsp & 0xf) <= (SP & 0xf)) F |= F_H;
            if ((SP & 0xf) - (offset & 0xf) < 0) F |= F_H;
            //A -= n;
            //System.err.println(nsp);
            if ((nsp & 0xFF00) != 0) F |= F_C;
            //       F |= F_N;
        }

        nsp &= 0xffff;

//                            if (((SP & 0xFFF) + (offset & 0xFFF)) > 0xFFF)
//                            {
//                                F |= F_H;
//                            }
//
//                            if (nsp > 0xFFFF)
//                            {
//                                F |= F_C;
//                                nsp &= 0xFFFF;
//                            }

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
        F = (short) ((F & F_C) != 0 ? (F & F_Z) : ((F & F_Z) | F_C));
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

        if (offset >= 0)
        {
            if ((SP & 0xff) + offset > 0xff) F |= F_C;
            if ((((SP & 0xf) + (offset & 0xf)) & 0xF0) != 0) F |= F_H;
        } else
        {
//                                if ((nsp & 0xff) <= (SP & 0xff)) F |= F_C;
//                                if ((nsp & 0xf) <= (SP & 0xf)) F |= F_H;
            if ((SP & 0xf) - (offset & 0xf) < 0) F |= F_H;
            //A -= n;
            //System.err.println(nsp);
            if ((nsp & 0xFF00) != 0) F |= F_C;
            F |= F_N;
        }

        nsp &= 0xffff;

//                            if (((SP & 0xFFF) + (offset & 0xFFF)) > 0xFFF)
//                            {
//                                F |= F_H;
//                            }
//
//                            if (nsp > 0xFFFF)
//                            {
//                                F |= F_C;
//                                nsp &= 0xFFFF;
//                            }

        setRegisterPair(HL, nsp);
        return 0;
    }

    public int CPL(int op)
    {
        A = (short) ((~A) & 0xFF);
        F = (short) ((F & (F_C | F_Z)) | F_H | F_N);
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
        setByte(getRegisterPair(HL) & 0xFFFF, A);
        setRegisterPair(HL, (getRegisterPair(HL) - 1) & 0xFFFF);

        return 0;
    }

    public int STOP(int op)
    {
        return NOP(op);
    }

    {
        instrs[0x00] = this::NOP;
        instrs[0xC4] = this::CALL_cc_nn;
        instrs[0xCC] = this::CALL_cc_nn;
        instrs[0xD4] = this::CALL_cc_nn;
        instrs[0xDC] = this::CALL_cc_nn;
        instrs[0xCD] = this::CALL_nn;
        instrs[0x01] = this::LD_dd_nn;
        instrs[0x11] = this::LD_dd_nn;
        instrs[0x21] = this::LD_dd_nn;
        instrs[0x31] = this::LD_dd_nn;
        instrs[0x06] = this::LD_r_n;
        instrs[0x0E] = this::LD_r_n;
        instrs[0x16] = this::LD_r_n;
        instrs[0x1E] = this::LD_r_n;
        instrs[0x26] = this::LD_r_n;
        instrs[0x2E] = this::LD_r_n;
        instrs[0x36] = this::LD_r_n;
        instrs[0x3E] = this::LD_r_n;
        instrs[0x0A] = this::LD_A_BC;
        instrs[0x1A] = this::LD_A_DE;
        instrs[0x02] = this::LD_BC_A;
        instrs[0x12] = this::LD_DE_A;
        instrs[0xF2] = this::LD_A_C;
        instrs[0xE8] = this::ADD_SP_n;
        instrs[0x37] = this::SCF;
        instrs[0x3F] = this::CCF;
        instrs[0x3A] = this::LD_A_n;
        instrs[0xEA] = this::LD_nn_A;
        instrs[0xF8] = this::LDHL_SP_n;
        instrs[0x2F] = this::CPL;
        instrs[0xE0] = this::LD_FFn_A;
        instrs[0xE2] = this::LDH_FFC_A;
        instrs[0xFA] = this::LD_A_nn;
        instrs[0x2A] = this::LD_A_HLI;
        instrs[0x22] = this::LD_HLI_A;
        instrs[0x32] = this::LD_HLD_A;
        instrs[0x10] = this::STOP;
    }

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

        if (instrs[op] != null)
        {
            instrs[op].call(op);
        } else
            outer:
                    switch (op)
                    {
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
                            int val = getRegisterPair2(RegisterPair.byValue[(op >> 4) & 0x3]);
                            pushWord(val);
                            return 4;
                        }
                        // POP qq
                        case 0xc1: // BC
                        case 0xd1: // DE
                        case 0xe1: // HL
                        case 0xf1: // AF
                        {

                            // System.out.println(">>>" + SP);
                            setRegisterPair2(RegisterPair.byValue[(op >> 4) & 0x3], getByte(SP + 1), getByte(SP));
//                if(op == 0xc1)
//                    System.err.println("POPPED " + Integer.toHexString(getRegisterPair2(P_BC) & 0xFFFF));
//                System.err.println("POPPED " + Integer.toHexString(getRegisterPair2((op >> 4) & 0x3) & 0xFFFF));
                            SP += 2;
                            break;
                        }
                        // LD (a16), SP
                        case 0x08:
                        {
                            int pos = ((nextUByte()) | (nextUByte() << 8));
                            setByte(pos + 1, (SP & 0xFF00) >> 8);
                            setByte(pos, (SP & 0x00FF));
                            break;
                        }
                        // RETI (EXX on z80)
                        case 0xd9:
                        {
                            interruptsEnabled = true;
                            pc = (getUByte(SP + 1) << 8) | getUByte(SP);
                            SP += 2;
                            return 4;
                        }
                        // JP nn
                        case 0xc3:
                        {
                            // n n n n n n n n
                            // n n n n n n n n
                            pc = (nextUByte()) | (nextUByte() << 8);
                            // System.out.printf("> branching to %04X\n", pc);
                            return 4;
                        }
                        // RLCA
                        case 0x07:
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
                            short reg = (short) ((op >> 3) & 0x7);
                            short a = (short) ((getRegister(reg)) & 0xff);

                            F &= F_C;
                            if ((((a & 0xf) + 1) & 0xF0) != 0) F |= F_H;
                            a++;
                            a &= 0xFF;
                            if (a == 0) F |= F_Z;

                            setRegister(reg, a);
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
                            short reg = (short) ((short) (op >> 3) & 0x7);
                            short a = (short) ((getRegister(reg)) & 0xff);

                            // 1 0 0 0  0 0 0 0
                            // 0 1 1 1
                            F &= F_C;
                            F |= F_N;
                            if ((a & 0xf) - 1 < 0) F |= F_H;
                            a--;
                            a &= 0xFF;
                            if (a == 0) F |= F_Z;

                            setRegister(reg, a);
                            break;
                        }
                        // INC ss
                        case 0x3:
                        case 0x13:
                        case 0x23:
                        case 0x33:
                        {
                            RegisterPair pair = RegisterPair.byValue[(op >> 4) & 0x3];
                            int o = getRegisterPair(pair) & 0xffff;
                            setRegisterPair(pair, o + 1);
                            doOamBug(o);
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
                            short n = (short) (getRegister(op & 0x7) & 0xFF);
                            F = F_N;
                            if (A < n) F |= F_C;
                            if (A == n) F |= F_Z;
                            if ((A & 0xf) < ((A - n) & 0xf)) F |= F_H;
                            break;
                        }
                        // CP s
                        case 0xfe:
                        {
                            int n = nextUByte();

                            F = F_N;
                            if (A < n) F |= F_C;
                            if (A == n) F |= F_Z;
                            if ((A & 0xf) < ((A - n) & 0xf)) F |= F_H;
                            break;
                        }
                        // ADD HL, ss
                        case 0x09:
                        case 0x19:
                        case 0x29:
                        case 0x39:
                        {
                            /**
                             * Z is not affected
                             * H is set if carry out of bit 11; reset otherwise
                             * N is reset
                             * C is set if carry from bit 15; reset otherwise
                             */
                            int ss = getRegisterPair(RegisterPair.byValue[(op >> 4) & 0x3]);
                            int hl = getRegisterPair(HL);

                            F = (short) (F & F_Z);

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
                            break;
                        }
                        // JP (HL)
                        case 0xe9:
                        {
                            pc = getRegisterPair(HL) & 0xFFFF;
                            break;
                        }
                        // SBC A, n
                        case 0xde:
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

                            break;
                        }
                        // SUB A, n
                        case 0xd6:
                        {
                            int n = nextUByte();
                            F = F_N;
                            if ((A & 0xf) - (n & 0xf) < 0) F |= F_H;
                            A -= n;
                            if ((A & 0xFF00) != 0) F |= F_C;
                            A &= 0xFF;
                            if (A == 0) F |= F_Z;
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
                            short n = (short) (getRegister(op & 0b111) & 0xff);
                            F = F_N;
                            if ((A & 0xf) - (n & 0xf) < 0) F |= F_H;
                            A -= n;
                            if ((A & 0xFF00) != 0) F |= F_C;
                            A &= 0xFF;
                            if (A == 0) F |= F_Z;
                            break;
                        }
                        // ADD A, n
                        case 0xc6:
                        {
                            int n = nextUByte();

                            F = 0;
                            if ((((A & 0xf) + (n & 0xf)) & 0xF0) != 0) F |= F_H;
                            A += n;
                            if (A > 0xFF)
                            {
                                F |= F_C;
                                A &= 0xFF;
                            }
                            if (A == 0) F |= F_Z;

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
                            short n = (short) (getRegister(op & 0b111) & 0xff);

                            F = 0;
                            if ((((A & 0xf) + (n & 0xf)) & 0xF0) != 0) F |= F_H;
                            A += n;
                            if (A > 0xFF)
                            {
                                F |= F_C;
                                A &= 0xFF;
                            }
                            if (A == 0) F |= F_Z;

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
                            A = (short) ((A & getRegister(op & 0b111)) & 0xff);
                            F = F_H;
                            if (A == 0) F |= F_Z;
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
                        {
                            A = (short) ((A ^ getRegister(op & 0b111)) & 0xff);
                            F = 0;
                            if (A == 0) F |= F_Z;
                            break;
                        }
                        // OR n
                        case 0xf6:
                        {
                            int n = nextUByte();
                            A |= n;
                            F = 0;
                            if (A == 0) F |= F_Z;
                            break;
                        }
                        // OR A, s
                        case 0xb0:
                        case 0xb1:
                        case 0xb2:
                        case 0xb3:
                        case 0xb4:
                        case 0xb5:
                        case 0xb6: // (HL)
                        case 0xb7:
                        {
                            A |= getRegister(op & 0b111) & 0xff;
                            F = 0;
                            if (A == 0) F |= F_Z;
                            break;
                        }
                        // JR e
                        case 0x18:
                        {
                            int e = nextByte();
                            //  System.out.printf("> branching to %04X\n", (pc + e));

                            pc += e;
                            return 4;
                        }
                        // DAA
                        case 0x27:
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
                                if ((F & F_H) != 0) tmp = (short) ((tmp - 6) & 0xff);
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
                            break;
                        }
                        // JP C, nn
                        case 0xca:
                        case 0xc2: // NZ
                        case 0xd2:
                        case 0xda:
                        {
                            int npc = nextUByte() | (nextUByte() << 8);
                            if (getConditionalFlag(0b100 | ((op >> 3) & 0x7)))
                            {
                                pc = npc;
                                return 4;
                            }
                            return 0;
                        }
                        // JR C, e
                        case 0x20: // NZ
                        case 0x28:
                        case 0x30:
                        case 0x38:
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
                        // LDH A, (FFnn)
                        case 0xf0:
                        {
                            A = getUByte(0xFF00 | nextUByte());
                            break;
                        }
                        // HALT
                        case 0x76:
                        {
                            cpuHalted = true;
                            return 0;
                        }
                        // RET cc
                        case 0xc0: // NZ non zero (Z)
                        case 0xc8: // Z zero (Z)
                        case 0xd0: // NC non carry (C)
                        case 0xd8: // Carry (C)
                        {
                            if (getConditionalFlag(0b100 | ((op >> 3) & 0x7)))
                            {
                                //pc = ((memory[SP] & 0xff) << 8) | (memory[SP+1] & 0xff);
                                pc = (getUByte(SP + 1) << 8) | getUByte(SP);
                                SP += 2;
                            }
                            return 4;
                        }
                        // RST p
                        case 0xc7:
                        case 0xcf:
                        case 0xd7:
                        case 0xdf:
                        case 0xe7:
                        case 0xef:
                        case 0xf7:
                        case 0xff:
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
                        // DI
                        case 0xf3:
                        {
                            //   System.err.println("Disabled interrupts");
                            interruptsEnabled = false;
                            break;
                        }
                        // EI
                        case 0xfb:
                        {
                            interruptsEnabled = true;
                            // Note that during the execution of this instruction and the following instruction,
                            // maskable interrupts are disabled.

                            // we still need to increment div etc
                            updateInterrupts(4);
                            return _exec();
                        }
                        // AND nn
                        case 0xE6:
                        {
                            A &= nextUByte();
                            F = F_H;
                            if (A == 0) F |= F_Z;
                            break;
                        }
                        // XOR nn
                        case 0xEE:
                        {
                            A ^= nextUByte();
                            F = 0;
                            if (A == 0) F |= F_Z;
                            break;
                        }
                        // RET
                        case 0xc9:
                        {
                            pc = (getUByte(SP + 1) << 8) | getUByte(SP);
                            SP += 2;
                            return 4;
                        }
                        // ADC A, n
                        case 0xce:
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
                            break;
                        }

                        // SBC A, s
                        case 0x98:
                        case 0x99:
                        case 0x9a:
                        case 0x9b:
                        case 0x9c:
                        case 0x9d:
                        case 0x9e: // (HL)
                        case 0x9f:
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
                            break;
                        }
                        case 0x0F: // RRCA
                        {
                            F = 0;//F_Z;
                            if ((A & 0x1) == 0x1) F |= F_C;
                            A >>= 1;
                            // we're shifting circular right, add back bit 7
                            if ((F & F_C) != 0) A |= 0x80;
                            break;
                        }
                        case 0x1f: // RRA
                        {
                            boolean carryflag = (F & F_C) != 0;
                            F = 0;
                            // we'll be shifting right, so if bit 1 is set we set carry
                            if ((A & 0x1) == 0x1) F |= F_C;
                            A >>= 1;
                            // move old C into bit 7
                            if (carryflag) A |= 0x80;
                            break;
                        }
                        case 0x17: // RLA
                        {
                            boolean carryflag = (F & F_C) != 0;
                            F = 0;//&= F_Z;
                            // we'll be shifting left, so if bit 7 is set we set carry
                            if ((A & 0x80) == 0x80) F |= F_C;
                            A <<= 1;
                            A &= 0xff;
                            // move old C into bit 0
                            if (carryflag) A |= 1;
                            break;
                        }
                        // DEC ss
                        case 0x0b:
                        case 0x1b:
                        case 0x2b:
                        case 0x3b:
                        {
                            RegisterPair p = RegisterPair.byValue[(op >> 4) & 0x3];
                            int o = getRegisterPair(p);
                            setRegisterPair(p, o - 1);
                            doOamBug(o);
                            break;
                        }

                        // CB prefix group
                        case 0xcb:
                        {
                            int x = pc++;

                            int cbop = getUByte(x);
                            short r = (short) (cbop & 7);
                            short d = (short) (getRegister(r) & 0xff);

                            switch ((cbop & 0b11000000))
                            {
                                // RES b, r
                                // 1 0 b b b r r r
                                case 0x80:
                                {
                                    setRegister(r, (short) (d & ~(0x1 << (cbop >> 3 & 0x7))));
                                    break outer;
                                }
                                // SET b, r
                                // 1 1 b b b r r r
                                case 0xc0:
                                {
                                    setRegister(r, (short) (d | (0x1 << (cbop >> 3 & 0x7))));
                                    break outer;
                                }
                                // BIT b, r
                                // 0 1 b b b r r r
                                case 0x40:
                                {
                                    F &= F_C;
                                    F |= F_H;
                                    if ((d & (0x1 << (cbop >> 3 & 0x7))) == 0) F |= F_Z;
                                    break outer;
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
                                            break outer;
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
                                            break outer;
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
                                            break outer;
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
                                            break outer;
                                        }
                                        case 0x38: // SRL m
                                        {
                                            F = 0;
                                            // we'll be shifting right, so if bit 1 is set we set carry
                                            if ((d & 0x1) != 0) F |= F_C;
                                            d >>= 1;
                                            if (d == 0) F |= F_Z;
                                            setRegister(r, d);
                                            break outer;
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
                                            break outer;
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
                                            break outer;
                                        }
                                        case 0x30: // SWAP m
                                        {
                                            d = (short) (((d & 0xF0) >> 4) | ((d & 0x0F) << 4));
                                            F = d == 0 ? F_Z : 0;
                                            setRegister(r, d);
                                            break outer;
                                        }
                                        default:
                                            throw new UnsupportedOperationException("cb-&f8-" + Integer.toHexString(cbop));
                                    }
                                }
                                default:
                                    throw new UnsupportedOperationException("cb-" + Integer.toHexString(cbop));
                            }
                        }
                        default:
                            switch (op & 0xC0)
                            {
                                case 0x40: // LD r, r'
                                {
                                    // 0 1 r r r r' r' r'
                                    int from = op & 0x7;
                                    int to = (op >> 3) & 0x7;
                                    // important note: getIO(6) fetches (HL)
                                    setRegister(to, (short) (getRegister(from) & 0xFF));
                                    break;
                                }
                                default:
                                    throw new UnsupportedOperationException(cycle + "-" + Integer.toHexString(op));
                            }
                    }
        return 0;
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


    public static final BufferedImage screenBuffer = new BufferedImage(160, 144, BufferedImage.TYPE_INT_ARGB);
    public static JPanel display;

    public static void main(String[] argv) throws IOException, ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException
    {
        // System.setProperty("sun.java2d.opengl", "True");
        System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream("emu.log"))));

        File f = new File(argv[0]);
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        in.read(buf);
        in.close();


        Cartridge cartridge = new Cartridge(buf);

        Emulator core = new Emulator(cartridge);

        File savefile = new File(cartridge.gameTitle + ".sav");

        if (core.mmu.hasBattery())
        {
            try
            {
                core.mmu.load(new FileInputStream(savefile));
            } catch (Exception e)
            {

            }
        }

        Thread codeExecutionThread = new Thread(() -> {
            try
            {
                core.exec();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        });

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> {
            JFrame disp = new JFrame(cartridge.gameTitle);
            disp.setContentPane(display = new JPanel()
            {
                {
                    int mag = 2;
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
                    addKeyListener(toggler);
                    disp.addKeyListener(toggler);
                }

                @Override
                public void paintComponent(Graphics g)
                {
                    //super.paintComponent(g);
                    //   ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(screenBuffer, 0, 0, getWidth(), getHeight(), null);
                }
            });
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
        ac += 4;
        executed += 4;
        cycle += 4;
        mmu.setAddress(addr, _data);
    }

    public void setIO(int addr, int data)
    {
        mmu.setIO(addr, data);
    }

    public int getUByte(int addr)
    {
        return getByte(addr) & 0xff;
    }

    public int getByte(int addr)
    {
        ac += 4;
        executed += 4;
        cycle += 4;
        return mmu.getAddress(addr);
    }

    public int getIO(int addr)
    {
        return mmu.getIO(addr);
    }
}
