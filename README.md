# Custom SQL query extension

This Postchain extension provides an GTX module which can make custom SQL queries against the database.

Enable and configure it by putting this into `chromia.yml`:

```yaml
blockchains:
  my-rell-dapp:
    module: main
    config:
      gtx:
        modules:
          - "net.postchain.gtx.extensions.customsqlquery.CustomSQLQueryGTXModuleFactory"
      customsqlquery:
        queries:
          query1: >
            SELECT col1, col2, col3, col4, col5
            FROM [table:my_entity]
            WHERE col1 = :arg1:text: AND col2 = :arg2:integer: AND col3 = :arg3:big_integer: AND
            col4 = :arg4:byte_array:
          query2: >
            SELECT col1, col2, col3, col4, col5
            FROM [table:other.entity1],[table:other.entity2]
            WHERE col1 = :arg1:text: AND col2 = :arg2:integer: AND col3 = :arg3:big_integer: AND
            col4 = :arg4:byte_array:
```

To access the tables of Rell entities and objects, use the syntax `[table:mount_name]`, which will be replaced with the
proper chain ID prefix. This can be used multiple times in order to join multiple tables. 

Arguments are specified using the syntax `:arg_name:type:` where type can be one of these Rell types:
* integer
* big_integer
* text
* byte_array

The response of queries will be an array of dictionaries representing the SQL result set.
