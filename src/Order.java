import java.util.Objects;

public record Order(int id, String shoeType, int quantity) {
    public Order {
        if (id <= 0 || quantity <= 0) throw new IllegalArgumentException("ID and quantity must be positive");
        Objects.requireNonNull(shoeType, "shoeType");
        if (shoeType.isBlank()) throw new IllegalArgumentException("Shoe type must not be blank");
    }
}