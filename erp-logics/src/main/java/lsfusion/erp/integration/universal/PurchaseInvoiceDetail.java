package lsfusion.erp.integration.universal;


import java.math.BigDecimal;
import java.sql.Date;

public class PurchaseInvoiceDetail {
    public Boolean isPosted;
    public String numberUserInvoice;
    public Date dateUserInvoice;
    public String idSupplier;
    public String currencyUserInvoice;
    public String idUserInvoiceDetail;
    public String idBarcodeSku;
    public String idBatch;
    public Integer dataIndex;
    public String idItem;
    public String idItemGroup;
    public String originalCustomsGroupItem;
    public String captionItem;
    public String originalCaptionItem;
    public String idUOM;
    public String idManufacturer;
    public String nameManufacturer;
    public String nameCountry;
    public String nameOriginCountry;
    public String nameImportCountry;
    public String idCustomer;
    public String idCustomerStock;
    public BigDecimal quantity;
    public BigDecimal price;
    public BigDecimal sum;
    public BigDecimal valueVAT;
    public BigDecimal sumVAT;
    public Date dateVAT;
    public String countryVAT;
    public BigDecimal invoiceSum;
    public BigDecimal manufacturingPrice;
    public String contractPrice;
    public BigDecimal shipmentPrice;
    public BigDecimal shipmentSum;
    public BigDecimal rateExchange;
    public String numberCompliance;
    public Date dateCompliance;
    public String numberDeclaration;
    public Date expiryDate;
    public Date manufactureDate;
    public String idPharmacyPriceGroup;
    public String seriesPharmacy;
    public String idArticle;
    public String captionArticle;
    public String originalCaptionArticle;
    public String idColor;
    public String nameColor;
    public String idCollection;
    public String nameCollection;
    public String idSize;
    public String nameSize;
    public String nameOriginalSize;
    public String idSeasonYear;
    public String idSeason;
    public String nameSeason;
    public String idBrand;
    public String nameBrand;
    public String idBox;
    public String nameBox;
    public String idTheme;
    public String nameTheme;
    public BigDecimal netWeight;
    public BigDecimal sumNetWeight;
    public BigDecimal grossWeight;
    public BigDecimal sumGrossWeight;
    public String composition;
    public String originalComposition;

    public PurchaseInvoiceDetail(Boolean isPosted, String numberUserInvoice, Date dateUserInvoice, String idSupplier, 
                                 String currencyUserInvoice, String idUserInvoiceDetail, String idBarcodeSku, 
                                 String idBatch, Integer dataIndex, String idItem, String idItemGroup, 
                                 String originalCustomsGroupItem, String captionItem, String originalCaptionItem, 
                                 String idUOM, String idManufacturer, String nameManufacturer, String nameCountry, 
                                 String nameOriginCountry, String nameImportCountry, String idCustomer, 
                                 String idCustomerStock, BigDecimal quantity, BigDecimal price, BigDecimal sum, 
                                 BigDecimal valueVAT, BigDecimal sumVAT, Date dateVAT, String countryVAT, 
                                 BigDecimal invoiceSum, BigDecimal manufacturingPrice, String contractPrice, 
                                 BigDecimal shipmentPrice, BigDecimal shipmentSum, BigDecimal rateExchange, 
                                 String numberCompliance, Date dateCompliance, String numberDeclaration, Date expiryDate,
                                 Date manufactureDate, String idPharmacyPriceGroup, String seriesPharmacy, 
                                 String idArticle, String captionArticle, String originalCaptionArticle, String idColor,
                                 String nameColor, String idCollection, String nameCollection, String idSize, 
                                 String nameSize, String nameOriginalSize, String idSeasonYear, String idSeason, 
                                 String nameSeason, String idBrand, String nameBrand, String idBox, String nameBox, 
                                 String idTheme, String nameTheme, BigDecimal netWeight, BigDecimal sumNetWeight, 
                                 BigDecimal grossWeight, BigDecimal sumGrossWeight, String composition, 
                                 String originalComposition) {
        this.isPosted = isPosted;
        this.numberUserInvoice = numberUserInvoice;
        this.dateUserInvoice = dateUserInvoice;
        this.idSupplier = idSupplier;
        this.currencyUserInvoice = currencyUserInvoice;
        this.idUserInvoiceDetail = idUserInvoiceDetail;
        this.idBarcodeSku = idBarcodeSku;
        this.idBatch = idBatch;
        this.dataIndex = dataIndex;
        this.idItem = idItem;
        this.idItemGroup = idItemGroup;
        this.originalCustomsGroupItem = originalCustomsGroupItem;
        this.captionItem = captionItem;
        this.originalCaptionItem = originalCaptionItem;
        this.idUOM = idUOM;
        this.idManufacturer = idManufacturer;
        this.nameManufacturer = nameManufacturer;
        this.nameCountry = nameCountry;
        this.nameOriginCountry = nameOriginCountry;
        this.nameImportCountry = nameImportCountry;
        this.idCustomer = idCustomer;
        this.idCustomerStock = idCustomerStock;
        this.quantity = quantity;
        this.price = price;
        this.sum = sum;
        this.valueVAT = valueVAT;
        this.sumVAT = sumVAT;
        this.dateVAT = dateVAT;
        this.countryVAT = countryVAT;
        this.invoiceSum = invoiceSum;
        this.manufacturingPrice = manufacturingPrice;
        this.contractPrice = contractPrice;
        this.shipmentPrice = shipmentPrice;
        this.shipmentSum = shipmentSum;
        this.rateExchange = rateExchange;
        this.numberCompliance = numberCompliance;
        this.dateCompliance = dateCompliance;
        this.numberDeclaration = numberDeclaration;
        this.expiryDate = expiryDate;
        this.manufactureDate = manufactureDate;
        this.idPharmacyPriceGroup = idPharmacyPriceGroup;
        this.seriesPharmacy = seriesPharmacy;
        this.idArticle = idArticle;
        this.captionArticle = captionArticle;
        this.originalCaptionArticle = originalCaptionArticle;
        this.idColor = idColor;
        this.nameColor = nameColor;
        this.idCollection = idCollection;
        this.nameCollection = nameCollection;
        this.idSize = idSize;
        this.nameSize = nameSize;
        this.nameOriginalSize = nameOriginalSize;
        this.idSeasonYear = idSeasonYear;
        this.idSeason = idSeason;
        this.nameSeason = nameSeason;
        this.idBrand = idBrand;
        this.nameBrand = nameBrand;
        this.idBox = idBox;
        this.nameBox = nameBox;
        this.idTheme = idTheme;
        this.nameTheme = nameTheme;
        this.netWeight = netWeight;
        this.sumNetWeight = sumNetWeight;
        this.grossWeight = grossWeight;
        this.sumGrossWeight = sumGrossWeight;
        this.composition = composition;
        this.originalComposition = originalComposition;
    }
}
