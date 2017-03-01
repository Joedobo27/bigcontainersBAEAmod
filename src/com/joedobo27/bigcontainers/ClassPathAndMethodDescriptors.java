package com.joedobo27.bigcontainers;

public enum ClassPathAndMethodDescriptors {
    CREATION_ENTRY_CLASS("com.wurmonline.server.items.CreationEntry", "CreationEntry", ""),
    ITEM_CLASS("com.wurmonline.server.items.Item", "Item", ""),
    CARGO_TRANSPORTATION_METHODS_CLASS("com.wurmonline.server.behaviours.CargoTransportationMethods", "CargoTransportationMethods", ""),
    MOVE_TO_ITEM_METHOD("com.wurmonline.server.items.Item","moveToItem", "(Lcom/wurmonline/server/creatures/Creature;JZ)Z"),
    CHECK_SANE_AMOUNTS_METHOD("","checkSaneAmounts",
            "(Lcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/ItemTemplate;Lcom/wurmonline/server/creatures/Creature;Z)V"),
    HAS_SPACE_FOR_METHOD("","hasSpaceFor","(I)Z"),
    TARGET_CANNOT_BE_INSERTED_CHECK_METHOD("", "targetCanNotBeInsertedCheck",
            "(Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Vehicle;Lcom/wurmonline/server/creatures/Creature;)Z"),
    INSERT_ITEM_METHOD("","insertItem", "(Lcom/wurmonline/server/items/Item;ZZ)Z"),
    TEST_INSERT_HOLLOW_ITEM_METHOD("", "testInsertHollowItem", "(Lcom/wurmonline/server/items/Item;Z)Z");

    private String path;
    private String name;
    private String descriptor;

    ClassPathAndMethodDescriptors(String path, String name, String descriptor){
        this.path = path;
        this.name = name;
        this.descriptor = descriptor;
    }

    public String getPath() {
        return this.path;
    }

    public String getName() {
        return this.name;
    }

    public String getDescriptor() {
        return this.descriptor;
    }
}
