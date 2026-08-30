# mcProxy

マイクラサーバー用のnginxみたいな感じ

## use

実行ディレクトリに設定ファイルを作る  
例としてこんな感じ

```json
{
  "settingVer": "0.0.1",
  "serverPort": 25565,
  "routings": [
    {
      "host": "localhost",
      "remoteHost": "127.0.0.1",
      "port": 25566
    },
    {
      "host": "127.0.0.1",
      "remoteHost": "127.0.0.1",
      "port": 25567
    }
  ]
}
```

にすると  
localhost =>127.0.0.1:25566  
127.0.0.1 =>127.0.0.1:25567  
にルートされる

```bash
java -jar app-all.jar
```

で実行
