package playzone.people;

public class Student extends Borrower {
    public Student(String name) {
        super(name);
    }

    @Override
    public double getDiscountRate() {
        return 0.10; // Student discount: 10%.
    }

    @Override
    public String getBorrowerType() {
        return "Student";
    }
}
