# Custom SQL query extension

This Postchain extension provides an GTX module which can make custom SQL queries against the database.

## Registration

```shell
pmc subnode-image add --name custom_sql_query \
  --url registry.gitlab.com/chromaway/core/custom-sql-query-extension/chromaway/custom-sql-query-extension \
  --digest ${DIGEST} \
  --image-description "Custom SQL query" \
  -gtx net.postchain.gtx.extensions.customsqlquery.CustomSQLQueryGTXModuleFactory
```

This will generate a proposal which need to be voted on.

## Configuration

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

## Updating the `chromia-subnode` base image

The base image is pinned by both tag and digest in `custom-sql-query-image/pom.xml` (the `<from><image>` element of the `jib-maven-plugin` configuration). The digest pin ensures reproducible builds; the version tag is kept alongside it for human readability.

To bump the base image (replace `<NEW_VERSION>` with the target tag):

```shell
docker pull registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-subnode:<NEW_VERSION>
docker inspect --format='{{index .RepoDigests 0}}' \
  registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-subnode:<NEW_VERSION>
```

Copy the resulting `sha256:…` digest and update the `<image>` line in `custom-sql-query-image/pom.xml` to:

```
registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-subnode:<NEW_VERSION>@<DIGEST>
```
