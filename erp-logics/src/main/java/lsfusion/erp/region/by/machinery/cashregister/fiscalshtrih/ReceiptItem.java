package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public Boolean isGiftCard;
    public BigDecimal price;
    public BigDecimal quantity;
    public String barcode;
    public String name;
    public BigDecimal sumPos;
    public BigDecimal discount;
    public BigDecimal valueVAT;

    public ReceiptItem(Boolean isGiftCard, BigDecimal price, BigDecimal quantity, String barcode, String name,
                       BigDecimal sumPos, BigDecimal discount, BigDecimal valueVAT) {
        this.isGiftCard = isGiftCard;
        this.price = price;
        this.quantity = quantity;
        this.barcode = barcode;
        this.name = name;
        this.sumPos = sumPos;
        this.discount = discount;
        this.valueVAT = valueVAT;
    }
}
