package playzone.exceptions;

// Checked exception for a request greater than current stock.
public class EquipmentNotAvailableException extends Exception {
    public EquipmentNotAvailableException(String message) {
        super(message);
    }
}
