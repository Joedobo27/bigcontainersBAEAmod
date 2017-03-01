package com.joedobo27.bigcontainers;

import com.joedobo27.common.Common;
import com.wurmonline.server.items.*;
import javassist.*;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.CodeReplacer;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.joedobo27.bigcontainers.BytecodeTools.addConstantPoolReference;
import static com.joedobo27.bigcontainers.BytecodeTools.findConstantPoolReference;
import static com.joedobo27.bigcontainers.ClassPathAndMethodDescriptors.*;


public class BigContainersBAEAMod implements WurmServerMod, Initable, ServerStartedListener, Configurable {

    private static boolean unlimitedSpace = false;
    private static boolean removeOnePerTileLimits = false;
    private static boolean resizePelt = false;
    private static boolean removeInsideOutsideLimits = false;
    private static ArrayList<Integer> makeItemsBulk = new ArrayList<>();
    private static ArrayList<Integer> makeItemsCombine = new ArrayList<>();

    private static ClassPool classPool = HookManager.getInstance().getClassPool();
    private static Logger logger = Logger.getLogger(BigContainersBAEAMod.class.getName());

    @Override
    public void configure(Properties properties) {
        unlimitedSpace = Boolean.parseBoolean(properties.getProperty("unlimitedSpace", Boolean.toString(unlimitedSpace)));
        removeOnePerTileLimits = Boolean.parseBoolean(properties.getProperty("removeOnePerTileLimits",
                Boolean.toString(removeOnePerTileLimits)));
        resizePelt = Boolean.parseBoolean(properties.getProperty("resizePelt", Boolean.toString(resizePelt)));
        removeInsideOutsideLimits = Boolean.parseBoolean(properties.getProperty("removeInsideOutsideLimits",
                Boolean.toString(removeInsideOutsideLimits)));

        if (properties.getProperty("makeBulkItems").length() > 0) {
            logger.log(Level.INFO, "makeBulkItems: " + properties.getProperty("makeBulkItems"));
            makeItemsBulk = Arrays.stream(properties.getProperty("makeBulkItems").replaceAll("\\s", "").split(","))
                    .mapToInt(Integer::parseInt)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (properties.getProperty("makeItemsCombine").length() > 0) {
            logger.log(Level.INFO, "makeItemsCombine: " + properties.getProperty("makeItemsCombine"));
            makeItemsCombine = Arrays.stream(properties.getProperty("makeItemsCombine")
                    .replaceAll("\\s", "").split(","))
                    .mapToInt(Integer::parseInt)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    @Override
    public void init() {
        try {
            if (!makeItemsCombine.isEmpty())
                checkSaneAmountsBytecodeAlter();
            if (unlimitedSpace) {
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

                evaluateChangesArray(successes, "unlimitedSpace");
            }
        } catch (NotFoundException | CannotCompileException | BadBytecode e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @Override
    public void onServerStarted() {
        try {
            int removeOnePerTileLimitsCount = removeOnePerTileLimitsReflection();
            resizePeltReflection();
            int makeItemsBulkCount = makeItemsBulkReflection(makeItemsBulk);
            int makeItemsCombineCount = makeItemsCombineReflection(makeItemsCombine);
            int removeInsideOutsideLimitsCount = removeInsideOutsideLimitsReflection();

            logger.log(Level.INFO, "Number of one per tile restrictions removed is " + Integer.toString(removeOnePerTileLimitsCount));
            logger.log(Level.INFO, "Number of items marked as bulk is " + Integer.toString(makeItemsBulkCount));
            logger.log(Level.INFO, "Number of items marked as combine is " + Integer.toString(makeItemsCombineCount));
            logger.log(Level.INFO, "Number of inside or outside only flags removed is " + Integer.toString(removeInsideOutsideLimitsCount));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    /**
     * In CreationEntry.checkSaneAmounts()
     * Byte code change: this.objectCreated != 73 to this.getObjectCreated() == 73
     * Do this because it's not possible to instrument on a field and have the replace function use a returned value
     * form a hook method.
     * Then, expression editor hook into getObjectCreated and replace returned with checkSaneAmountsExceptionsHook.
     * Using the hook instead of straight bytecode because it lets me use variable names form WU code.
     *
     * This change needs to be made because it blocks using very small things that can be combined.
     *
     * @throws BadBytecode JA related, forwarded
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static void checkSaneAmountsBytecodeAlter() throws BadBytecode, NotFoundException, CannotCompileException {
        final boolean[] toReturn = {false};
        JAssistClassData creationEntry = JAssistClassData.getClazz(CREATION_ENTRY_CLASS.getName());
        if (creationEntry == null)
            creationEntry = new JAssistClassData(CREATION_ENTRY_CLASS.getPath(), classPool);
        JAssistMethodData checkSaneAmounts = new JAssistMethodData(creationEntry, CHECK_SANE_AMOUNTS_METHOD.getDescriptor(),
                CHECK_SANE_AMOUNTS_METHOD.getName());

        boolean isModifiedCheckSaneAmounts = true;
        byte[] findPoolResult;
        try {
            findConstantPoolReference(creationEntry.getConstPool(),
                    "// Method com/joedobo27/common/Common.checkSaneAmountsExceptionsHook:(III)I");
        } catch (UnsupportedOperationException e) {
            isModifiedCheckSaneAmounts = false;
        }
        if (isModifiedCheckSaneAmounts)
            toReturn[0] = true;
        if (!isModifiedCheckSaneAmounts) {
            Bytecode find = new Bytecode(creationEntry.getConstPool());
            find.addOpcode(Opcode.ALOAD_0);
            find.addOpcode(Opcode.GETFIELD);
            findPoolResult = findConstantPoolReference(creationEntry.getConstPool(), "// Field objectCreated:I");
            find.add(findPoolResult[0], findPoolResult[1]);
            find.addOpcode(Opcode.BIPUSH);
            find.add(73);

            Bytecode replace = new Bytecode(creationEntry.getConstPool());
            replace.addOpcode(Opcode.ALOAD_0);
            replace.addOpcode(Opcode.INVOKEVIRTUAL);
            findPoolResult = addConstantPoolReference(creationEntry.getConstPool(), "// Method getObjectCreated:()I");
            replace.add(findPoolResult[0], findPoolResult[1]);
            replace.addOpcode(Opcode.BIPUSH);
            replace.add(73);

            CodeReplacer codeReplacer = new CodeReplacer(checkSaneAmounts.getCodeAttribute());
            try {
                codeReplacer.replaceCode(find.get(), replace.get());
            } catch (NotFoundException e){
                toReturn[0] = false;
            }

            checkSaneAmounts.getCtMethod().instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals("getObjectCreated", methodCall.getMethodName())) {
                        methodCall.replace("$_ = com.joedobo27.common.Common.checkSaneAmountsExceptionsHook( $0.getObjectCreated(), sourceMax, targetMax);");
                        logger.log(Level.FINE, "CreationEntry.class, checkSaneAmounts(), installed hook at line: " + methodCall.getLineNumber());
                        toReturn[0] = true;
                    }
                }
            });
        }
        evaluateChangesArray(toReturn[0] ? new int[]{1} : new int[]{0}, "makeItemsCombine");
    }

    /**
     * Alter Item.hasSpaceFor() so it always returns true using CtMethod.setBody().
     *
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static void hasSpaceForBytecodeAlter() throws NotFoundException, CannotCompileException{
        JAssistClassData itemClass = JAssistClassData.getClazz(ITEM_CLASS.getName());
        if (itemClass == null)
            itemClass = new JAssistClassData(ITEM_CLASS.getPath(), classPool);
        itemClass.getCtClass().getMethod(HAS_SPACE_FOR_METHOD.getName(), HAS_SPACE_FOR_METHOD.getDescriptor()).setBody("return true;");
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
        JAssistClassData cargoTransportationMethods = JAssistClassData.getClazz(CARGO_TRANSPORTATION_METHODS_CLASS.getName());
        if (cargoTransportationMethods == null)
            cargoTransportationMethods = new JAssistClassData(CARGO_TRANSPORTATION_METHODS_CLASS.getPath(), classPool);
        JAssistMethodData targetCanNotBeInsertedCheck = new JAssistMethodData(cargoTransportationMethods,
                TARGET_CANNOT_BE_INSERTED_CHECK_METHOD.getDescriptor(), TARGET_CANNOT_BE_INSERTED_CHECK_METHOD.getName());
        targetCanNotBeInsertedCheck.getCtMethod().instrument(new ExprEditor() {
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
        JAssistClassData itemClass = JAssistClassData.getClazz(ITEM_CLASS.getName());
        if (itemClass == null)
            itemClass = new JAssistClassData(ITEM_CLASS.getPath(), classPool);
        JAssistMethodData insertItem = new JAssistMethodData(itemClass,
                INSERT_ITEM_METHOD.getDescriptor(), INSERT_ITEM_METHOD.getName());
        insertItem.getCtMethod().instrument(new ExprEditor() {
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
        JAssistClassData itemClass = JAssistClassData.getClazz(ITEM_CLASS.getName());
        if (itemClass == null)
            itemClass = new JAssistClassData(ITEM_CLASS.getPath(), classPool);
        JAssistMethodData moveToItem = new JAssistMethodData(itemClass,
                MOVE_TO_ITEM_METHOD.getDescriptor(), MOVE_TO_ITEM_METHOD.getName());
        moveToItem.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getSizeX", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 3545){
                    logger.log(Level.FINE, "moveToItem method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.indexOfBytecode());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[0] = 1;
                }
                else if (Objects.equals("getSizeY", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 3566){
                    logger.log(Level.FINE, "moveToItem method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.indexOfBytecode());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[1] = 1;
                }
                else if (Objects.equals("getSizeZ", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 3587) {
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
        JAssistClassData itemClass = JAssistClassData.getClazz(ITEM_CLASS.getName());
        if (itemClass == null)
            itemClass = new JAssistClassData(ITEM_CLASS.getPath(), classPool);
        //Alter Item.testInsertHollowItem() so volume and measurements return max int.
        JAssistMethodData testInsertHollowItem = new JAssistMethodData(itemClass,
                TEST_INSERT_HOLLOW_ITEM_METHOD.getDescriptor(), TEST_INSERT_HOLLOW_ITEM_METHOD.getName());
        testInsertHollowItem.getCtMethod().instrument(new ExprEditor() {
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

    private static int removeOnePerTileLimitsReflection() throws NoSuchFieldException, IllegalAccessException {
        int removeOnePerTileLimitsCount = 0;
        if (!removeOnePerTileLimits)
            return removeOnePerTileLimitsCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldOnePerTile = ReflectionUtil.getField(ItemTemplate.class, "onePerTile");
        for (ItemTemplate template : fieldTemplates.values()) {
            Boolean onePerTile;
            onePerTile = ReflectionUtil.getPrivateField(template, fieldOnePerTile);
            if (onePerTile) {
                ReflectionUtil.setPrivateField(template, fieldOnePerTile, Boolean.FALSE);
                removeOnePerTileLimitsCount++;
            }
        }
        return removeOnePerTileLimitsCount;
    }

    private static void resizePeltReflection() throws NoSuchFieldException, IllegalAccessException {
        if (!resizePelt)
            return;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldCentimetersX = ReflectionUtil.getField(ItemTemplate.class, "centimetersX");
        Field fieldCentimetersY = ReflectionUtil.getField(ItemTemplate.class, "centimetersY");
        Field fieldCentimetersZ = ReflectionUtil.getField(ItemTemplate.class, "centimetersZ");
        Field fieldWeight = ReflectionUtil.getField(ItemTemplate.class, "weight");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            if (itemId == ItemList.pelt) {
                try {
                    ReflectionUtil.setPrivateField(template, fieldCentimetersX, 10);
                    ReflectionUtil.setPrivateField(template, fieldCentimetersY, 10);
                    ReflectionUtil.setPrivateField(template, fieldCentimetersZ, 10);
                    ReflectionUtil.setPrivateField(template, fieldWeight, 100);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                logger.log(Level.INFO, "Pelt resized.");
            }
        }
    }

    private static int makeItemsBulkReflection(List<Integer> makeBulk) throws NoSuchFieldException, IllegalAccessException {
        int makeItemsBulkCount = 0;
        if (makeItemsBulk.isEmpty())
            return makeItemsBulkCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldGem = ReflectionUtil.getField(ItemTemplate.class, "gem");
        Field fieldBulk = ReflectionUtil.getField(ItemTemplate.class, "bulk");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            Boolean isGem = ReflectionUtil.getPrivateField(template, fieldGem);
            if (template.isFish() || isGem || makeBulk.stream().filter(value -> Objects.equals(value, itemId)).count() > 0) {
                ReflectionUtil.setPrivateField(template, fieldBulk, Boolean.TRUE);
                makeItemsBulkCount++;
            }
        }
        return makeItemsBulkCount;
    }

    private static int makeItemsCombineReflection(List<Integer> makeCombine) throws NoSuchFieldException, IllegalAccessException {
        int makeItemsCombineCount = 0;
        if (makeItemsCombine.isEmpty())
            return makeItemsCombineCount;
        int[] exceptions = {ItemList.woad, ItemList.dyeBlue, ItemList.acorn, ItemList.tannin,
                ItemList.cochineal, ItemList.dyeRed, ItemList.dye, ItemList.lye};
        Common.addExceptions(exceptions);

        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldCombine = ReflectionUtil.getField(ItemTemplate.class, "combine");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            if (makeCombine.stream().filter(value -> Objects.equals(value, itemId)).count() > 0) {
                ReflectionUtil.setPrivateField(template, fieldCombine, Boolean.TRUE);
                makeItemsCombineCount++;
            }
        }
        return makeItemsCombineCount;
    }

    private static int removeInsideOutsideLimitsReflection() throws NoSuchFieldException, IllegalAccessException {
        int removeInsideOutsideLimitsCount = 0;
        if (!removeInsideOutsideLimits)
            return removeInsideOutsideLimitsCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field outsideOnly = ReflectionUtil.getField(ItemTemplate.class, "outsideonly");
        Field insideOnly = ReflectionUtil.getField(ItemTemplate.class, "insideOnly");
        for (ItemTemplate template : fieldTemplates.values()) {
            Boolean isInsideOnly = ReflectionUtil.getPrivateField(template, insideOnly);
            if (template.isOutsideOnly() || isInsideOnly) {
                try {
                    ReflectionUtil.setPrivateField(template, outsideOnly, Boolean.FALSE);
                    ReflectionUtil.setPrivateField(template, insideOnly, Boolean.FALSE);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                removeInsideOutsideLimitsCount++;
            }
        }
        return removeInsideOutsideLimitsCount;
    }

    @SuppressWarnings("unused")
    public static int getFreeVolumeHook(Item item) {
        return item.getContainerVolume() - getUsedVolume(item);
    }

    private static int getUsedVolume(Item item) {
        int used = 0;
        for (final Item i : item.getItems()) {
            if (!i.isInventoryGroup()) {
                if (i.isLiquid() || i.isBulkItem()) {
                    used += i.getWeightGrams();
                }
                else {
                    used += i.getVolume();
                }
            }
            else {
                used += getUsedVolume(i);
            }
        }
        return used;
    }

    private static void evaluateChangesArray(int[] ints, String option) {
        boolean changesSuccessful = Arrays.stream(ints).noneMatch(value -> value == 0);
        if (changesSuccessful) {
            logger.log(Level.INFO, option + " option changes SUCCESSFUL");
        } else {
            logger.log(Level.INFO, option + " option changes FAILURE");
            logger.log(Level.FINE, Arrays.toString(ints));
        }
    }
}