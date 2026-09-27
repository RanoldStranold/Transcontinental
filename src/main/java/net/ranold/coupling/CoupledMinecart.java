package net.ranold.coupling;

public interface CoupledMinecart {

    MinecartLinks transcontinental$links();

    int transcontinental$syncedPartner(int slot);

    void transcontinental$setSyncedPartner(int slot, int entityId);

    int transcontinental$syncedEnd(int slot);

    void transcontinental$setSyncedEnd(int slot, int end);
}
