package playzone.people;

public class Staff extends Borrower {
    public Staff(String name) {
        super(name);
    }

    @Override
    public double getDiscountRate() {
        return 0.15; // Staff discount: 15%.
    }

    @Override
    public String getBorrowerType() {
        return "Staff";
    }
}
