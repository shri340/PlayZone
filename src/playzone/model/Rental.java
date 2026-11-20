package playzone.model;

import playzone.Main;
import playzone.people.Borrower;

public class Rental {
    private static int rentalCounter = 0; // static counter creates RNT1, RNT2, ...
    private final String rentalId;
    private final Borrower borrower;
    private final RentalItem[] items;
    private final int tokenNumber;

    public Rental(Borrower borrower, RentalItem[] items, int tokenNumber) {
        this.borrower = borrower;
        this.items = items;
        this.tokenNumber = tokenNumber;
        rentalCounter++;
        this.rentalId = "RNT" + rentalCounter;
    }

    public String getRentalId() { return rentalId; }
    public Borrower getBorrower() { return borrower; }
    public RentalItem[] getItems() { return items; }
    public int getTokenNumber() { return tokenNumber; }

    // Recursive subtotal: each call adds one rental line.
    public double getSubtotal() {
        return subtotalRecursive(0);
    }

    private double subtotalRecursive(int index) {
        if (index >= items.length) {
            return 0.0;
        }
        return items[index].getLineTotal() + subtotalRecursive(index + 1);
    }

    public double getDiscountAmount() {
        return getSubtotal() * borrower.getDiscountRate();
    }

    public double getFinalTotal(double gstRate) {
        double discountedAmount = getSubtotal() - getDiscountAmount();
        return discountedAmount + (discountedAmount * gstRate);
    }

    public void printBill(double gstRate) {
        double subtotal = getSubtotal();
        double discount = getDiscountAmount();
        double discountedAmount = subtotal - discount;
        double gst = discountedAmount * gstRate;
        double total = discountedAmount + gst;

        System.out.println("\n=============== PLAYZONE BILL ===============");
        System.out.println("Rental ID       : " + rentalId);
        System.out.println("Borrower        : " + borrower.getName());
        System.out.println("Borrower type   : " + borrower.getBorrowerType());
        System.out.println("Token number    : " + tokenNumber);
        // Bitwise AND (& 1) checks whether the token is odd or even.
        String collectionRoom = (tokenNumber & 1) == 1 ? "Store Room A" : "Store Room B";
        System.out.println("Collect from    : " + collectionRoom);
        System.out.println("----------------------------------------------");
        System.out.printf("%-22s %-10s %-12s%n", "Equipment", "Quantity", "Amount");
        for (RentalItem item : items) {
            System.out.printf("%-22s %-10d ₹%-11.2f%n",
                    item.getEquipment().getName(), item.getQuantity(), item.getLineTotal());
        }
        System.out.println("----------------------------------------------");
        System.out.printf("Subtotal                 : ₹%.2f%n", subtotal);
        System.out.printf("Discount (%.0f%%)           : -₹%.2f%n",
                borrower.getDiscountRate() * 100, discount);
        System.out.printf("GST (%.0f%%)                : ₹%.2f%n", gstRate * 100, gst);
        System.out.printf("FINAL TOTAL              : ₹%.2f%n", total);
        System.out.println("==============================================");
    }

    @Override
    public String toString() {
        return rentalId + " | " + borrower.getName() + " | Token " + tokenNumber;
    }
}
