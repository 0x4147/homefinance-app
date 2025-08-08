//package ca.homefinance.mapper;
//
//import ca.homefinance.dto.TransactionDto;
//import ca.homefinance.entity.Transaction;
//import org.mapstruct.Mapper;
//import org.mapstruct.Mapping;
//import org.mapstruct.MappingTarget;
//
//@Mapper(componentModel = "spring")
//public interface TransactionMapper {
//
//    // Entity → DTO
//    @Mapping(target = "amount", expression = "java(entity.getAmount().toPlainString())")
//    @Mapping(target = "date", expression = "java(entity.getDate().toString())")
//    @Mapping(target = "account", expression = "java(entity.getAccount().name())")
//    @Mapping(target = "transactionType", expression = "java(entity.getTransactionType().name())")
//    @Mapping(target = "categoryId", expression = "java(entity.getCategory() != null ? entity.getCategory().getId().toString() : null)")
//    @Mapping(target = "personId", expression = "java(entity.getPerson() != null ? entity.getPerson().getId().toString() : null)")
//    TransactionDto toDto(Transaction transaction);
//
//    // DTO → Entity
//    @Mapping(target = "amount", expression = "java(new java.math.BigDecimal(dto.getAmount()))")
//    @Mapping(target = "date", expression = "java(java.time.LocalDate.parse(dto.getDate()))")
//    @Mapping(target = "account", expression = "java(ca.homefinance.entity.Transaction.AccountType.valueOf(dto.getAccount()))")
//    @Mapping(target = "transactionType", expression = "java(ca.homefinance.entity.Transaction.TransactionType.valueOf(dto.getTransactionType()))")
//    @Mapping(target = "category.id", expression = "java(Long.parseLong(dto.getCategoryId()))")
//    @Mapping(target = "person.id", expression = "java(Long.parseLong(dto.getPersonId()))")
//    Transaction toEntity(TransactionDto transactionDto);
//
//    @Mapping(target = "amount", expression = "java(entity.getAmount().toPlainString())")
//    @Mapping(target = "date", expression = "java(entity.getDate().toString())")
//    @Mapping(target = "account", expression = "java(entity.getAccount().name())")
//    @Mapping(target = "transactionType", expression = "java(entity.getTransactionType().name())")
//    @Mapping(target = "categoryId", expression = "java(entity.getCategory() != null ? entity.getCategory().getId().toString() : null)")
//    @Mapping(target = "personId", expression = "java(entity.getPerson() != null ? entity.getPerson().getId().toString() : null)")
//    void updateDtoFromEntity(Transaction transaction, @MappingTarget TransactionDto transactionDto);
//
//    @Mapping(target = "amount", expression = "java(new java.math.BigDecimal(dto.getAmount()))")
//    @Mapping(target = "date", expression = "java(java.time.LocalDate.parse(dto.getDate()))")
//    @Mapping(target = "account", expression = "java(ca.homefinance.entity.Transaction.AccountType.valueOf(dto.getAccount()))")
//    @Mapping(target = "transactionType", expression = "java(ca.homefinance.entity.Transaction.TransactionType.valueOf(dto.getTransactionType()))")
//    @Mapping(target = "category.id", expression = "java(Long.parseLong(dto.getCategoryId()))")
//    @Mapping(target = "person.id", expression = "java(Long.parseLong(dto.getPersonId()))")
//    void updateEntityFromDto(TransactionDto transactionDto, @MappingTarget TransactionDto transaction);
//
//}
