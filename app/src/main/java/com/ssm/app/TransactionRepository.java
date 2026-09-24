package com.ssm.app;
import android.content.Context;
import java.util.UUID;
public final class TransactionRepository {
    private final Context context;
    public TransactionRepository(Context c){this.context=c.getApplicationContext();}
    public boolean addParsed(Transaction tx){String rule;try(TransactionDb db=new TransactionDb(context)){rule=db.findMerchantRule(tx.merchant);Transaction finalTx=rule==null?tx:new Transaction(tx.id,tx.transactionId,tx.sourceKey,tx.sourcePackage,tx.merchant,tx.amount,tx.type,rule,tx.paymentMethod,tx.occurredAt,tx.rawText,tx.manual);long row=db.insertOrIgnore(finalTx);if(row<=0)return false;tx=finalTx;}SyncCoordinator.enqueueAllTargets(context,tx);return true;}
    public Transaction createManual(long amount,String type,String merchant,String category,String paymentMethod){long now=System.currentTimeMillis();String key="manual:"+UUID.randomUUID();String id=TransactionId.create("manual",key,amount,type,now,merchant);Transaction tx=new Transaction(0L,id,key,"manual",merchant,amount,type,category,paymentMethod,now,"직접 입력",true);addParsed(tx);return tx;}
    public boolean correct(String transactionId,String merchant,String category,String paymentMethod){try(TransactionDb db=new TransactionDb(context)){Transaction old=db.find(transactionId);if(old==null)return false;Transaction fixed=new Transaction(old.id,old.transactionId,old.sourceKey,old.sourcePackage,merchant,old.amount,old.type,category,paymentMethod,old.occurredAt,old.rawText,old.manual);if(!db.update(fixed))return false;db.saveMerchantRule(merchant,category);db.resetSyncTargets(transactionId);}SyncCoordinator.schedule(context);return true;}
    public boolean delete(String transactionId){try(TransactionDb db=new TransactionDb(context)){return db.delete(transactionId);}}
}
