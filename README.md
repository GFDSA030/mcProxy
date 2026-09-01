# mcProxy

マイクラサーバー用のnginxみたいな感じ

## use

実行ディレクトリに設定ファイルを作る  
例としてこんな感じ  
hostにIPアドレスを指定すると動かない場合があるため要ドメイン指定

```jsonc:setting.json
{
  "settingVer": "0.0.1",//設定バージョン
  "serverPort": 25565,//プロキシ待機ポート
  "infoPort":28080,//ユーザー情報配信ポート
  "pin": "908",//配信pin
  "routings": [//ルーティング
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
127.0.0.1 =>127.0.0.1:25567(動かない可能性大)  
にルートされる

```bash
java -jar mcProxy-all.jar
```

で実行
