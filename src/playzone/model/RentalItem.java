package playzone.model;

public class RentalItem {
    private final Equipment equipment;
    private final int quantity;

    public RentalItem(Equipment equipment, int quantity) {
        this.equipment = equipment;
        this.quantity = quantity;
    }

    public Equipment getEquipment() { return equipment; }
    public int getQuantity() { return quantity; }

    public double getLineTotal() {
        return equipment.getRentPerDay() * quantity;
    }
}
