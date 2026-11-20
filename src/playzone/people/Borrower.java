package playzone.people;

public abstract class Borrower extends Person implements Billable {
    public Borrower(String name) {
        super(name); // passes the name to Person constructor.
    }

    public abstract String getBorrowerType();

    @Override
    public String getPersonCategory() {
        return getBorrowerType();
    }
}
