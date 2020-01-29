## Fee Schedule Update tools

There are two tools to help update FeeSchedule on a network:
1. UpdateFeeSchedule: Signs and executes fee schedule update transactions.
2. Signer and Uploader: Signer per-signs the transactions and stores them in a file. Uploader
   executes the transactions to update fee schedule on the network. For mainnet use case, since the signer (entity owning
   the keys) will be different than person executing the transactions (devops).

### UpdateFeeSchedule
Steps:
1. Set NODE_ID, NODE_ADDRESS, FEE_SCHEDULE_OWNER_ACCOUNT, FEE_SCHEDULE_OWNER_KEY, filePath
2. Comment/uncomment appropriate actions in main()
3. Run the tool

### Signer and Uploader

#### Signer
Given feeSchedule.txt(proto file), signer will chunk it into 5k pieces, create one FileUpdate & zero or more FileAppend transactions,
sign them, and write results to a file.
To account for uncertainty of upload time and since transactionValidDuration can be set to maximum 180 sec, signer generates many sets
of fee schedule update+append transactions with start time 120 seconds apart.
Start time for these transactions' sets can be configured by changing `WINDOW_START`.
Window of time for which the sets will be generated can be configured using `WINDOW_DURATION_SEC`.
For eg. if `WINDOW_START` is `2020-01-01 03:00:00` and `WINDOW_DURATION_SEC` is 2 days, then the signer will generated following
set of signed transactions:
```
validStartTime, [set of signed transactions]
1577890800 => [fileUpdate1, fileAppend1...] // Corresponding to 2020-01-01 03:00:00
1577890920 => [fileUpdate2, fileAppend2...]
...
...
1578063480 => [fileUpdateN, fileAppendN...]  // Corresponding to 2020-01-03 02:58:00
```

#### Uploader
Uploader takes in the file written by the Signer, gets the right set of transactions based on current epoch timestamp,
and then executes & verifies them (one at a time).




