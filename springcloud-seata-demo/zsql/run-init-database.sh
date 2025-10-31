

docker exec -i seata-mysql mysql -uroot -proot123 < seata_account.sql
docker exec -i seata-mysql mysql -uroot -proot123 < seata_order.sql
docker exec -i seata-mysql mysql -uroot -proot123 < seata_storage.sql