package playzone.people;

public abstract class Person {
    private final String name;

    public Person(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // final method: subclasses cannot change this identity display.
    public final String getIdentityCard() {
        return "Borrower: " + name;
    }

    public abstract String getPersonCategory();
}
