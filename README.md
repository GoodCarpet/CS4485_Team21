# CS 4485.0W1 Team 21 Project - Sentence Builder
---

## DATABASE INFO
---
all DB info is shared in pinned messages in discord
### Connect via MySQL Workbench
- download [MySQL Workbench](https://downloads.mysql.com/archives/workbench/)
- add a new connection
- copy and paste all the info into the parameters tab
- goto ssl tab
  - change use ssl to require and verify CA
  - download ca.pem from discord
  - use the path in the ssl CA section
- try and connect, it should work

### Connect via Java
You must make an environment variable on your computer first
- open system properties on your pc
- click on environment variables
- under user variable, click new
- add new variables named: "SB_DB_PASSWORD", "SB_DB_URL", "SB_DB_USER"
- variable values must be the ones from pinned Discord messages
- hit ok
