package ca.homefinance.service;

import ca.homefinance.repository.ReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReceiptService {

    @Autowired
    private ReceiptRepository receiptRepository;

//    public Optional<Receipt> addReceipt(String id, String fileName){
//        Receipt receipt = receiptRepository.insert(new Receipt(fileName));
//
//        mongoTemplate.update(Transaction.class)
//                .matching(Criteria.where("id").is(id))
//                .apply(new Update().push("receipts").value(receipt))
//                .first();
//
//        return Optional.of(receipt);
//    }
}
