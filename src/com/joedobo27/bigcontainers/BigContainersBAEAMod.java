package com.joedobo27.bigcontainers;


import javassist.*;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BigContainersBAEAMod implements WurmServerMod, Initable, Configurable {

    private static final String[] STEAM_VERSION = new String[]{"1.3.4.4"};
    private static boolean versionCompliant = true;
    private static Logger logger = Logger.getLogger(BigContainersBAEAMod.class.getName());

    @Override
    public void configure(Properties properties) {
        if (Arrays.stream(STEAM_VERSION)
                .filter(s -> Objects.equals(s, properties.getProperty("steamVersion", null)))
                .count() > 0)
            versionCompliant = true;
        else
            logger.log(Level.WARNING, "WU version mismatch. Your " + properties.getProperty(" steamVersion", null)
                    + "version doesn't match one of BulkOptionsMod's required versions " + Arrays.toString(STEAM_VERSION));
    }

    @Override
    public void init() {
        if (!versionCompliant)
            return;
        try {
            int[] successes = new int[12];
            Arrays.fill(successes, 0);
            int[] result;

            hasSpaceForBytecodeAlter();
            result = targetCanNotBeInsertedCheckBytecodeAlter();
            System.arraycopy(result,0, successes, 0, 4);

            result = insertItemBytecodeAlter();
            System.arraycopy(result,0, successes, 4, 2);

            result = moveToItemBytecodeAlter();
            System.arraycopy(result,0, successes, 6, 4);

            result = testInsertHollowItemBytecodeAlter();
            System.arraycopy(result,0, successes, 10, 2);

            evaluateChangesArray(successes);

        } catch (NotFoundException | CannotCompileException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    /**
     * Alter Item.hasSpaceFor() so it always returns true using CtMethod.setBody().
     *
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static void hasSpaceForBytecodeAlter() throws NotFoundException, CannotCompileException{
        CtClass itemCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
        CtClass returnType = CtPrimitiveType.booleanType;
        CtClass[] paramTypes = {CtPrimitiveType.intType};
        CtMethod hasSpaceForCt = itemCt.getMethod("hasSpaceFor", Descriptor.ofMethod(returnType, paramTypes));
        hasSpaceForCt.setBody("return true;");
    }

    /**
     * Alter CargoTransportationMethods.targetCanNotBeInsertedCheck() so the volume and x,y,z measurements return max int.
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static int[] targetCanNotBeInsertedCheckBytecodeAlter() throws NotFoundException, CannotCompileException{
        int[] successes = new int[4];
        Arrays.fill(successes, 0);

        CtClass cargoTransportationMethodsCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.CargoTransportationMethods");
        CtClass[] paramTypes = {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Vehicle"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
        };
        CtMethod targetCanNotBeInsertedCheckCt = cargoTransportationMethodsCt.getDeclaredMethod("targetCanNotBeInsertedCheck", paramTypes);
        targetCanNotBeInsertedCheckCt.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getContainerSizeX", methodCall.getMethodName())){
                    logger.log(Level.FINE, "targetCanNotBeInsertedCheck method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[0] = 1;
                }
                else if (Objects.equals("getContainerSizeY", methodCall.getMethodName())){
                    logger.log(Level.FINE, "targetCanNotBeInsertedCheck method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[1] = 1;
                }
                else if (Objects.equals("getContainerSizeZ", methodCall.getMethodName())){
                    logger.log(Level.FINE, "targetCanNotBeInsertedCheck method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[2] = 1;
                }
                else if (Objects.equals("getFreeVolume", methodCall.getMethodName())){
                    logger.log(Level.FINE, "targetCanNotBeInsertedCheck method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[3] = 1;
                }
            }
        });
        return successes;
    }

    /**
     * Alter Item.insertItem() so getFreeVolume() returns max integer and itemCanBeInserted is always true.
     *      final int freevol = this.getFreeVolume();
     *      if (unconditionally || this.itemCanBeInserted(item)) {...}
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static int[] insertItemBytecodeAlter() throws NotFoundException, CannotCompileException {
        int[] successes = new int[2];
        Arrays.fill(successes, 0);

        CtClass itemCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
        CtClass returnType = CtPrimitiveType.booleanType;
        CtClass[] paramtypes = {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                CtPrimitiveType.booleanType, CtPrimitiveType.booleanType
        };
        CtMethod insertItemCt = itemCt.getMethod("insertItem", Descriptor.ofMethod(returnType, paramtypes));
        insertItemCt.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getFreeVolume", methodCall.getMethodName())) {
                    logger.log(Level.FINE, "insertItem method, edit call to " + methodCall.getMethodName() +
                            " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[0] = 1;
                }
                else if (Objects.equals("itemCanBeInserted", methodCall.getMethodName())) {
                    logger.log(Level.FINE, "insertItem method, edit call to " + methodCall.getMethodName() +
                            " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = true;");
                    successes[1] = 1;
                }
            }
        });
        return successes;
    }

    /**
     * Edit the target.getSize?() returned values to be integer.MAX_VALUE. This makes it so the container's measurements are always
     * larger then the object being placed inside it.
     *  if (target.getSizeX() < this.getSizeX() || target.getSizeY() < this.getSizeY() || target.getSizeZ() <= this.getSizeZ()) {...}
     *
     *  the getLineNumber values include that whole if statement and prevents identify with getLineNumber.
     *  The Bytecode indexes change with each insert so it won't match javap indexes.
     *
     * Alter hasSpaceFor so it always true.
     *  if (!target.isCrate() && target.hasSpaceFor(this.getVolume())) {
     *
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static int[] moveToItemBytecodeAlter() throws NotFoundException, CannotCompileException {
        int[] successes = new int[4];
        Arrays.fill(successes, 0);

        CtClass itemCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
        CtClass returnType = CtPrimitiveType.booleanType;
        CtClass[] paramTypes = {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                CtPrimitiveType.longType, CtPrimitiveType.booleanType
        };
        CtMethod moveToItemCt = itemCt.getMethod("moveToItem", Descriptor.ofMethod(returnType, paramTypes));
        moveToItemCt.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getSizeX", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 3600){
                    logger.log(Level.FINE, "moveToItem method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.indexOfBytecode());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[0] = 1;
                }
                else if (Objects.equals("getSizeY", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 3621 ){
                    logger.log(Level.FINE, "moveToItem method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.indexOfBytecode());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[1] = 1;
                }
                else if (Objects.equals("getSizeZ", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 3642) {
                    logger.log(Level.FINE, "moveToItem method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.indexOfBytecode());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[2] = 1;
                }
                else if (Objects.equals("hasSpaceFor", methodCall.getMethodName())) {
                    logger.log(Level.FINE, "moveToItem method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = true;");
                    successes[3] = 1;
                }
            }
        });
        return successes;
    }

    /**
     * Alter getFreeVolume() so it returns Integer.MAX_VALUE.
     *  final int freevol = this.getFreeVolume();
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static int[] testInsertHollowItemBytecodeAlter() throws NotFoundException, CannotCompileException {
        int[] successes = new int[2];
        Arrays.fill(successes, 0);

        CtClass itemCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
        CtClass[] paramTypes = {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"), CtPrimitiveType.booleanType
        };
        CtMethod testInsertHollowItemCt = itemCt.getDeclaredMethod("testInsertHollowItem", paramTypes);
        testInsertHollowItemCt.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getFreeVolume", methodCall.getMethodName())) {
                    logger.log(Level.FINE, "testInsertHollowItem method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[0] = 1;
                }
                else if (Objects.equals("itemCanBeInserted", methodCall.getMethodName())) {
                    logger.log(Level.FINE, "testInsertHollowItem method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = true;");
                    successes[1] = 1;
                }
            }
        });
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