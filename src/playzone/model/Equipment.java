package playzone.model;

public class Equipment {
    private final int id;
    private final String name;
    private final String category;
    private final double rentPerDay;
    private int units;

    public Equipment(int id, String name, String category, double rentPerDay, int units) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.rentPerDay = rentPerDay;
        this.units = units;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getRentPerDay() { return rentPerDay; }
    public int getUnits() { return units; }

    public void reduceUnits(int quantity) {
        units -= quantity;
    }

    // Used by the overloaded equipment search methods.
    public boolean matches(int searchId) {
        return id == searchId;
    }

    public boolean matches(String searchName) {
        return name.equalsIgnoreCase(searchName);
    }

    @Override
    public String toString() {
        return id + " - " + name + " (" + category + "), ₹"
                + rentPerDay + "/day, units: " + units;
    }
}
