package playzone.service;

// Custom stack implemented with arrays only (LIFO: Last In, First Out).
public class ItemStack {
    private final int[] equipmentIndexes;
    private final int[] quantities;
    private int top = -1;
    private int lastRemovedQuantity = 0;

    public ItemStack(int capacity) {
        equipmentIndexes = new int[capacity];
        quantities = new int[capacity];
    }

    public void push(int equipmentIndex, int quantity) {
        if (isFull()) {
            throw new IllegalStateException("Item stack is full.");
        }
        top++;
        equipmentIndexes[top] = equipmentIndex;
        quantities[top] = quantity;
    }

    public int pop() {
        if (isEmpty()) {
            throw new IllegalStateException("Item stack is empty.");
        }
        int removedIndex = equipmentIndexes[top];
        lastRemovedQuantity = quantities[top];
        top--;
        return removedIndex;
    }

    public boolean isEmpty() { return top == -1; }
    public boolean isFull() { return top == equipmentIndexes.length - 1; }
    public int size() { return top + 1; }
    public int getLastRemovedQuantity() { return lastRemovedQuantity; }

    public int[] getIndexes() {
        int[] result = new int[size()];
        for (int i = 0; i < size(); i++) result[i] = equipmentIndexes[i];
        return result;
    }

    public int[] getQuantities() {
        int[] result = new int[size()];
        for (int i = 0; i < size(); i++) result[i] = quantities[i];
        return result;
    }
}
