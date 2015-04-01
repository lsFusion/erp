package equ.clt.handler.ukm4mysql;

import equ.api.SalesBatch;
import equ.api.SalesInfo;
import lsfusion.base.Pair;

import java.util.List;
import java.util.Set;

public class UKM4MySQLSalesBatch extends SalesBatch {
    Set<Pair<Integer, Integer>> receiptSet;
    Set<Pair<Integer, Integer>> receiptItemSet;

    public UKM4MySQLSalesBatch(List<SalesInfo> salesInfoList, Set<Pair<Integer, Integer>>  receiptSet, Set<Pair<Integer, Integer>> receiptItemSet) {
        this.salesInfoList = salesInfoList;
        this.receiptSet = receiptSet;
        this.receiptItemSet = receiptItemSet;
    }
}
