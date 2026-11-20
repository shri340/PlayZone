package playzone.exceptions;

// Checked exception: extends Exception, so callers must catch or declare it.
public class EquipmentNotFoundException extends Exception {
    public EquipmentNotFoundException(String message) {
        super(message);
    }
}
