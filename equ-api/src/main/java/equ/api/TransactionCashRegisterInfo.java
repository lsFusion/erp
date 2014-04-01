package equ.api;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class TransactionCashRegisterInfo extends TransactionInfo<CashRegisterInfo, CashRegisterItemInfo> {
    public Integer nppGroupCashRegister;
    
    public TransactionCashRegisterInfo(Integer id, String dateTimeCode, Date date, List<CashRegisterItemInfo> itemsList,
                                       List<CashRegisterInfo> machineryInfoList, Integer nppGroupCashRegister) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.date = date;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
        this.nppGroupCashRegister = nppGroupCashRegister;
    }

    @Override
    public void sendTransaction(Object handler, List<CashRegisterInfo> machineryInfoList) throws IOException {
        ((CashRegisterHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
