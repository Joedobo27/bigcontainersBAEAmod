package com.joedobo27.bigcontainers;


import javassist.*;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Descriptor;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.CodeReplacer;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BigContainersBAEAMod implements WurmServerMod, Initable {


    private static Logger logger = Logger.getLogger(BigContainersBAEAMod.class.getName());

    @Override
    public void init() {
        int[] successes = new int[10];
        Arrays.fill(successes, 0);
        int[] result;

        result = hasSpaceForBytecodeAlter();
        System.arraycopy(result,0, successes, 0, 1);

        result = targetCanNotBeInsertedCheckBytecodeAlter();
        System.arraycopy(result,0, successes, 1, 4);

        result = insertItemBytecodeAlter();
        System.arraycopy(result,0, successes, 5, 2);

        result = moveToItemBytecodeAlter();
        System.arraycopy(result,0, successes, 7, 1);

        result = testInsertHollowItemBytecodeAlter();
        System.arraycopy(result,0, successes, 8, 2);

        evaluateChangesArray(successes);
    }

    /**
     * Alter Item.hasSpaceFor() so it always returns true using CtMethod.setBody().
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     */
    private static int[] hasSpaceForBytecodeAlter() {
        int[] toReturn = {0};
        try {
            CtClass ItemCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
            CtClass returnType = CtPrimitiveType.booleanType;
            CtClass[] paramTypes = {CtPrimitiveType.intType};
            CtMethod hasSpaceForCt = ItemCt.getMethod("hasSpaceFor", Descriptor.ofMethod(returnType, paramTypes));
            hasSpaceForCt.setBody("return true;");
        }catch (NotFoundException | CannotCompileException e){
            logger.fine(e.getMessage());
            return toReturn;
        }
        toReturn[0] = 1;
        return toReturn;
    }

    /**
     * Alter CargoTransportationMethods.targetCanNotBeInsertedCheck() so the volume and x,y,z measurements return max int.
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     */
    private static int[] targetCanNotBeInsertedCheckBytecodeAlter() {
        int[] successes = new int[4];
        Arrays.fill(successes, 0);

        try {
            CtClass cargoTransportationMethodsCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.CargoTransportationMethods");
            CtClass[] paramTypes = {
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Vehicle"),
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature")
            };
            CtMethod targetCanNotBeInsertedCheckCt = cargoTransportationMethodsCt.getDeclaredMethod("targetCanNotBeInsertedCheck", paramTypes);
            targetCanNotBeInsertedCheckCt.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals("getContainerSizeX", methodCall.getMethodName())) {
                        logger.log(Level.FINE, "targetCanNotBeInsertedCheck method,  edit call to " +
                                methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                        methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                        successes[0] = 1;
                    } else if (Objects.equals("getContainerSizeY", methodCall.getMethodName())) {
                        logger.log(Level.FINE, "targetCanNotBeInsertedCheck method,  edit call to " +
                                methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                        methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                        successes[1] = 1;
                    } else if (Objects.equals("getContainerSizeZ", methodCall.getMethodName())) {
                        logger.log(Level.FINE, "targetCanNotBeInsertedCheck method,  edit call to " +
                                methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                        methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                        successes[2] = 1;
                    } else if (Objects.equals("getFreeVolume", methodCall.getMethodName())) {
                        logger.log(Level.FINE, "targetCanNotBeInsertedCheck method,  edit call to " +
                                methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                        methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                        successes[3] = 1;
                    }
                }
            });
        } catch (NotFoundException | CannotCompileException e){
            logger.fine(e.getMessage());
            return successes;
        }
        return successes;
    }

    /**
     * Alter Item.insertItem() so getFreeVolume() returns max integer and itemCanBeInserted is always true.
     *      final int freevol = this.getFreeVolume();
     *      if (unconditionally || this.itemCanBeInserted(item)) {...}
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     */
    private static int[] insertItemBytecodeAlter() {
        int[] successes = new int[2];
        Arrays.fill(successes, 0);
        try {
            CtClass itemCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
            CtClass returnType = CtPrimitiveType.booleanType;
            CtClass[] paramTypes = {itemCt, CtPrimitiveType.booleanType, CtPrimitiveType.booleanType};
            CtMethod insertItemCt = itemCt.getMethod("insertItem", Descriptor.ofMethod(returnType, paramTypes));
            insertItemCt.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals("getFreeVolume", methodCall.getMethodName())) {
                        logger.log(Level.FINE, "insertItem method, edit call to " + methodCall.getMethodName() +
                                " at index " + methodCall.getLineNumber());
                        methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                        successes[0] = 1;
                    } else if (Objects.equals("itemCanBeInserted", methodCall.getMethodName())) {
                        logger.log(Level.FINE, "insertItem method, edit call to " + methodCall.getMethodName() +
                                " at index " + methodCall.getLineNumber());
                        methodCall.replace("$_ = true;");
                        successes[1] = 1;
                    }
                }
            });
        }catch (NotFoundException | CannotCompileException e){
            logger.fine(e.getMessage());
            return successes;
        }
        return successes;
    }

    /**
     * Edit the Item.moveToItem() to simply skip over the follow code block.
     *  if (target.getSizeX() < this.getSizeX() || target.getSizeY() < this.getSizeY() || target.getSizeZ() <= this.getSizeZ()) {...}
     *
     *  This edit doesn't work well with ExprEditor because in order to pinpoint a change methodCall.indexOfBytecode() is needed.
     *  For some reason trying to replace the logic block with NOPs causes stack map problems. Instead pop the values returned from
     *  getSize?(), push a zero into the stack in there place and do a IFEQ branch.
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     */
    private static int[] moveToItemBytecodeAlter() {
        int[] successes = new int[]{0};

        try {
            CtClass itemCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
            CtClass returnType = CtPrimitiveType.booleanType;
            CtClass[] paramTypes = {
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                    CtPrimitiveType.longType, CtPrimitiveType.booleanType
            };
            CtMethod moveToItemCt = itemCt.getMethod("moveToItem", Descriptor.ofMethod(returnType, paramTypes));

            BytecodeTools find = new BytecodeTools(itemCt.getClassFile().getConstPool());
            find.addAload(5);
            find.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeX", "()I", "com.wurmonline.server.items.Item");
            find.addOpcode(Opcode.ALOAD_0);
            find.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeX", "()I", "com.wurmonline.server.items.Item");
            find.codeBranching(Opcode.IF_ICMPLT, 465);
            find.addAload(5);
            find.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeY", "()I", "com.wurmonline.server.items.Item");
            find.addOpcode(Opcode.ALOAD_0);
            find.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeY", "()I", "com.wurmonline.server.items.Item");
            find.codeBranching(Opcode.IF_ICMPLT, 453);
            find.addAload(5);
            find.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeZ", "()I", "com.wurmonline.server.items.Item");
            find.addOpcode(Opcode.ALOAD_0);
            find.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeZ", "()I", "com.wurmonline.server.items.Item");
            find.codeBranching(Opcode.IF_ICMPLE, 441);

            BytecodeTools replace = new BytecodeTools(itemCt.getClassFile().getConstPool());
            replace.addAload(5);
            replace.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeX", "()I", "com.wurmonline.server.items.Item");
            replace.addOpcode(Opcode.ALOAD_0);
            replace.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeX", "()I", "com.wurmonline.server.items.Item");
            replace.addOpcode(Opcode.POP2);
            replace.addOpcode(Opcode.ICONST_0);
            replace.codeBranching(Opcode.IFEQ, 469);
            replace.addAload(5);
            replace.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeY", "()I", "com.wurmonline.server.items.Item");
            replace.addOpcode(Opcode.ALOAD_0);
            replace.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeY", "()I", "com.wurmonline.server.items.Item");
            replace.addOpcode(Opcode.POP2);
            replace.addOpcode(Opcode.ICONST_0);
            replace.codeBranching(Opcode.IFEQ, 455);
            replace.addAload(5);
            replace.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeZ", "()I", "com.wurmonline.server.items.Item");
            replace.addOpcode(Opcode.ALOAD_0);
            replace.findMethodIndex(Opcode.INVOKEVIRTUAL, "getSizeZ", "()I", "com.wurmonline.server.items.Item");
            replace.addOpcode(Opcode.POP2);
            replace.addOpcode(Opcode.ICONST_0);
            replace.codeBranching(Opcode.IFEQ, 441);

            CodeReplacer codeReplacer = new CodeReplacer(moveToItemCt.getMethodInfo().getCodeAttribute());
            codeReplacer.replaceCode(find.get(), replace.get());
            moveToItemCt.getMethodInfo().rebuildStackMapIf6(HookManager.getInstance().getClassPool(), itemCt.getClassFile());
            successes[0] = 1;

        }catch (NotFoundException | BadBytecode e){
            logger.fine(e.getMessage());
            return successes;
        }
        return successes;
    }

    /**
     * Alter Item.testInsertHollowItem() so:
     * 1. "final int freevol = this.getFreeVolume();" is always Integer.MAX_VALUE.
     * 2. "this.itemCanBeInserted(item)" is always true.
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     */
    private static int[] testInsertHollowItemBytecodeAlter() {
        int[] successes = new int[2];
        Arrays.fill(successes, 0);
        try {
            CtClass itemCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
            CtClass[] paramTypes = {itemCt, CtPrimitiveType.booleanType};
            CtMethod testInsertHollowItemCt = itemCt.getDeclaredMethod("testInsertHollowItem", paramTypes);
            testInsertHollowItemCt.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals("getFreeVolume", methodCall.getMethodName())) {
                        logger.log(Level.FINE, "testInsertHollowItem method,  edit call to " +
                                methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                        methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                        successes[0] = 1;
                    } else if (Objects.equals("itemCanBeInserted", methodCall.getMethodName())) {
                        logger.log(Level.FINE, "testInsertHollowItem method,  edit call to " +
                                methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                        methodCall.replace("$_ = true;");
                        successes[1] = 1;
                    }
                }
            });
        }catch (NotFoundException | CannotCompileException e){
            logger.fine(e.getMessage());
            return successes;
        }
        return successes;
    }

    private static void evaluateChangesArray(int[] ints) {
        boolean changesSuccessful = Arrays.stream(ints).noneMatch(value -> value == 0);
        if (changesSuccessful) {
            logger.log(Level.INFO, "unlimitedSpace option changes SUCCESS");
        } else {
            logger.log(Level.INFO, "unlimitedSpace option changes FAILURE");
            logger.log(Level.FINE, Arrays.toString(ints));
        }
    }
}