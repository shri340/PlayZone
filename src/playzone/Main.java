package playzone;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

import playzone.model.Equipment;
import playzone.model.Rental;
import playzone.people.Borrower;
import playzone.people.Student;
import playzone.people.Staff;
import playzone.exceptions.EquipmentNotFoundException;
import playzone.exceptions.EquipmentNotAvailableException;
import playzone.service.ItemStack;

public class Main {
    // GST is fixed as required by the problem statement.
    public static final double GST = 0.18;

    private static final int MAX_RENTALS = 500;
    private static final int MAX_ITEMS_PER_RENTAL = 100;

    // Unit I: Equipment and Rental arrays (no ArrayList / HashMap).
    private static final Equipment[] equipment = {
        new Equipment(101, "Cricket Bat", "Cricket", 80.0, 10),
        new Equipment(102, "Football", "Football", 50.0, 12),
        new Equipment(103, "Badminton Racket", "Badminton", 40.0, 20),
        new Equipment(104, "Tennis Racket", "Tennis", 90.0, 8),
        new Equipment(105, "Basketball", "Basketball", 50.0, 10),
        new Equipment(106, "Carrom Board", "Indoor", 60.0, 6)
    };

    private static final Rental[] rentals = new Rental[MAX_RENTALS];
    private static int rentalCount = 0;
    private static int nextToken = 1;

    public static void main(String[] args) {
        // try-with-resources: Scanner is automatically closed at program exit.
        try (Scanner scanner = new Scanner(System.in)) {
            char menuChoice;

            // Unit II: do-while menu loop.
            do {
                printMenu();
                int choice = readInt(scanner, "Enter your choice: ");

                // char is used for the menu selection as required by Unit I.
                menuChoice = (char) ('0' + choice);

                // Unit II: switch statement.
                switch (menuChoice) {
                    case '1':
                        showEquipment();
                        break;
                    case '2':
                        createRental(scanner);
                        break;
                    case '3':
                        viewOldBill(scanner);
                        break;
                    case '4':
                        showReport();
                        break;
                    case '5':
                        System.out.println("Thank you for using PlayZone!");
                        break;
                    default:
                        System.out.println("Invalid menu choice. Please enter 1 to 5.");
                }
            } while (menuChoice != '5');
        } catch (Exception e) {
            // Final safety net: unexpected input/runtime issues should not show a stack trace.
            System.out.println("A program error was handled safely: " + e.getMessage());
        } finally {
            System.out.println("PlayZone session ended.");
        }
    }

    private static void printMenu() {
        System.out.println("\n========== PLAYZONE ==========");
        System.out.println("1. View equipment list");
        System.out.println("2. New rental");
        System.out.println("3. View bill");
        System.out.println("4. Rental report");
        System.out.println("5. Exit");
        System.out.println("==============================");
    }

    private static void showEquipment() {
        System.out.println("\n---------------- EQUIPMENT LIST ----------------");
        System.out.printf("%-8s %-22s %-14s %-14s %-8s %-8s%n",
                "ID", "Equipment", "Category", "Rent/day", "Units", "Status");

        for (Equipment item : equipment) {
            // ?: operator: fewer than 10 units means LOW.
            String status = item.getUnits() < 10 ? "LOW" : "OK";
            System.out.printf("%-8d %-22s %-14s ₹%-13.2f %-8d %-8s%n",
                    item.getId(), item.getName(), item.getCategory(),
                    item.getRentPerDay(), item.getUnits(), status);
        }
        System.out.println("------------------------------------------------");
    }

    private static void createRental(Scanner scanner) {
        if (rentalCount >= rentals.length) {
            System.out.println("Rental storage is full. Cannot create another rental.");
            return;
        }

        System.out.println("\n========== NEW RENTAL ==========");
        String borrowerName = readNonEmptyLine(scanner, "Enter borrower name: ");
        readCurrentBorrowerName = borrowerName;
        Borrower borrower = readBorrower(scanner);

        // The custom stack stores the equipment lines added to this draft rental.
        ItemStack stack = new ItemStack(MAX_ITEMS_PER_RENTAL);
        int[] requestedTotals = new int[equipment.length];
        boolean rentalRejected = false;

        System.out.println("\nEnter equipment IDs and quantities.");
        System.out.println("Enter -1 to undo the last equipment line, or 0 to finish.");

        while (true) {
            int id = readInt(scanner, "\nEquipment ID: ");

            if (id == 0) {
                break;
            }

            if (id == -1) {
                if (stack.isEmpty()) {
                    System.out.println("Nothing to undo. The rental list is empty.");
                } else {
                    int removedIndex = stack.pop();
                    requestedTotals[removedIndex] -= stack.getLastRemovedQuantity();
                    System.out.println("Last equipment line removed successfully.");
                    // Recalculate totals from the stack to keep the draft consistent.
                    requestedTotals = calculateRequestedTotals(stack);
                }
                continue;
            }

            try {
                // Nested try: handle a bad equipment ID separately from stock errors.
                try {
                    int equipmentIndex = findEquipment(id);
                    Equipment selected = equipment[equipmentIndex];

                    int quantity = readInt(scanner, "Enter quantity: ");
                    if (quantity <= 0) {
                        System.out.println("Quantity must be a positive number. Try again.");
                        continue;
                    }

                    int alreadyRequested = requestedTotals[equipmentIndex];
                    if (alreadyRequested + quantity > selected.getUnits()) {
                        throw new EquipmentNotAvailableException(
                                "Only " + selected.getUnits() + " unit(s) of "
                                + selected.getName() + " are available. "
                                + "Your total request is " + (alreadyRequested + quantity) + ".");
                    }

                    if (stack.isFull()) {
                        System.out.println("Rental has reached its maximum number of item lines.");
                        continue;
                    }

                    stack.push(equipmentIndex, quantity);
                    requestedTotals[equipmentIndex] += quantity;
                    System.out.println(quantity + " x " + selected.getName() + " added.");
                } catch (EquipmentNotFoundException e) {
                    // Multiple catch blocks are used for the two checked exceptions.
                    System.out.println("EquipmentNotFoundException: " + e.getMessage());
                    System.out.println("Please enter a valid equipment ID. The program will continue.");
                }
            } catch (EquipmentNotAvailableException e) {
                System.out.println("EquipmentNotAvailableException: " + e.getMessage());
                System.out.println("Rental rejected. No stock has been changed.");
                rentalRejected = true;
                break;
            } catch (IllegalStateException e) {
                System.out.println("Rental input could not be added: " + e.getMessage());
            } finally {
                // finally executes whether the item is accepted or an exception occurs.
                System.out.println("Equipment entry processed.");
            }
        }

        if (rentalRejected) {
            logAction("Rejected rental for " + borrowerName + " (stock unchanged).");
            return;
        }

        if (stack.isEmpty()) {
            System.out.println("No equipment was selected. Rental was not created.");
            return;
        }

        // Build a compact item array from the stack. Stock is committed only now,
        // after every requested line has passed validation.
        int itemCount = stack.size();
        int[] itemIndexes = stack.getIndexes();
        int[] itemQuantities = stack.getQuantities();
        playzone.model.RentalItem[] rentalItems =
                new playzone.model.RentalItem[itemCount];

        for (int i = 0; i < itemCount; i++) {
            Equipment item = equipment[itemIndexes[i]];
            rentalItems[i] = new playzone.model.RentalItem(item, itemQuantities[i]);
        }

        // Commit stock changes only after successful validation of the whole rental.
        for (int i = 0; i < itemCount; i++) {
            equipment[itemIndexes[i]].reduceUnits(itemQuantities[i]);
        }

        int token = nextToken++;
        Rental rental = new Rental(borrower, rentalItems, token);
        rentals[rentalCount++] = rental;

        System.out.println("\nRental created successfully!");
        rental.printBill(GST);
        logAction("Created " + rental.getRentalId() + " for " + borrowerName + ".");
    }

    private static Borrower readBorrower(Scanner scanner) {
        while (true) {
            String type = readNonEmptyLine(scanner, "Borrower type (Student/Staff): ");
            if (type.equalsIgnoreCase("student") || type.equalsIgnoreCase("s")) {
                return new Student(readCurrentBorrowerName);
            }
            if (type.equalsIgnoreCase("staff") || type.equalsIgnoreCase("f")) {
                return new Staff(readCurrentBorrowerName);
            }
            System.out.println("Invalid borrower type. Enter Student or Staff.");
        }
    }

    // This field is set immediately before readBorrower() is called.
    private static String readCurrentBorrowerName;

    private static int findEquipment(int id)
            throws EquipmentNotFoundException {
        for (int i = 0; i < equipment.length; i++) {
            if (equipment[i].matches(id)) {
                return i;
            }
        }
        throw new EquipmentNotFoundException("Equipment ID " + id + " does not exist.");
    }

    // Overloaded method: equipment can also be searched by name.
    @SuppressWarnings("unused")
    private static int findEquipment(String name)
            throws EquipmentNotFoundException {
        for (int i = 0; i < equipment.length; i++) {
            if (equipment[i].matches(name)) {
                return i;
            }
        }
        throw new EquipmentNotFoundException("Equipment named " + name + " does not exist.");
    }

    private static int[] calculateRequestedTotals(ItemStack stack) {
        int[] totals = new int[equipment.length];
        int[] indexes = stack.getIndexes();
        int[] quantities = stack.getQuantities();
        for (int i = 0; i < stack.size(); i++) {
            totals[indexes[i]] += quantities[i];
        }
        return totals;
    }

    private static void viewOldBill(Scanner scanner) {
        String requestedId = readNonEmptyLine(scanner, "Enter rental ID (example: RNT1): ");
        boolean found = false;

        for (int i = 0; i < rentalCount; i++) {
            if (rentals[i].getRentalId().equalsIgnoreCase(requestedId)) {
                rentals[i].printBill(GST);
                found = true;
                break;
            }
        }

        if (!found) {
            System.out.println("No rental found with ID " + requestedId + ".");
        }
    }

    private static void showReport() {
        System.out.println("\n========== DAILY RENTAL REPORT ==========");
        double totalRevenue = 0.0;
        int[] popularity = new int[equipment.length];

        for (int i = 0; i < rentalCount; i++) {
            Rental rental = rentals[i];
            totalRevenue += rental.getFinalTotal(GST);

            playzone.model.RentalItem[] items = rental.getItems();
            for (playzone.model.RentalItem item : items) {
                for (int j = 0; j < equipment.length; j++) {
                    if (equipment[j].getId() == item.getEquipment().getId()) {
                        popularity[j] += item.getQuantity();
                        break;
                    }
                }
            }
        }

        int mostPopularIndex = -1;
        for (int i = 0; i < popularity.length; i++) {
            if (mostPopularIndex == -1 || popularity[i] > popularity[mostPopularIndex]) {
                mostPopularIndex = i;
            }
        }

        System.out.println("Total rentals : " + rentalCount);
        System.out.printf("Total revenue : ₹%.2f%n", totalRevenue);

        if (rentalCount == 0 || mostPopularIndex == -1 || popularity[mostPopularIndex] == 0) {
            System.out.println("Most popular equipment: No rentals yet.");
        } else {
            System.out.println("Most popular equipment: "
                    + equipment[mostPopularIndex].getName()
                    + " (" + popularity[mostPopularIndex] + " unit(s))");
        }
        System.out.println("=========================================");
    }

    private static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a whole number.");
            }
        }
    }

    private static String readNonEmptyLine(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("This field cannot be empty.");
        }
    }

    private static void logAction(String message) {
        // try-with-resources: closes the file writer automatically.
        try (PrintWriter writer = new PrintWriter(new FileWriter("AI_LOG.txt", true))) {
            writer.println(message);
        } catch (IOException e) {
            System.out.println("Could not write AI_LOG.txt: " + e.getMessage());
        }
    }
}
