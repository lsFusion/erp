package equ.api.cashregister;

import equ.api.ItemInfo;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

public class CashRegisterItemInfo extends ItemInfo {
    public Long itemGroupObject;
    public String description;
    public String idBrand;
    public String nameBrand;
    public String idSeason;
    public String nameSeason;
    public String idDepartmentStore;
    public String section;
    public String deleteSection;
    public BigDecimal minPrice;
    public String extIdItemGroup;
    public BigDecimal amountBarcode;
    public BigDecimal balance;
    public Timestamp restrictionToDateTime;
    public Long barcodeObject;

    public CashRegisterItemInfo(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry,
                                Date expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags,
                                String idItemGroup, String nameItemGroup, String idUOM, String shortNameUOM,
                                Long itemGroupObject, String description, String idBrand, String nameBrand, String idSeason, String nameSeason,
                                String idDepartmentStore, String section, String deleteSection, BigDecimal minPrice, String extIdItemGroup, BigDecimal amountBarcode,
                                BigDecimal balance, Timestamp restrictionToDateTime, Long barcodeObject) {
        super(null, idItem, idBarcode, name, price, splitItem, daysExpiry, expiryDate, passScales, vat, pluNumber, flags, idItemGroup, nameItemGroup,
                idUOM, shortNameUOM);
        this.itemGroupObject = itemGroupObject;
        this.description = description;
        this.idBrand = idBrand;
        this.nameBrand = nameBrand;
        this.idSeason = idSeason;
        this.nameSeason = nameSeason;
        this.idDepartmentStore = idDepartmentStore;
        this.section = section;
        this.deleteSection = deleteSection;
        this.minPrice = minPrice;
        this.extIdItemGroup = extIdItemGroup;
        this.amountBarcode = amountBarcode;
        this.balance = balance;
        this.restrictionToDateTime = restrictionToDateTime;
        this.barcodeObject = barcodeObject;
    }
}
