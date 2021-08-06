
[grafana](http://localhost:3000)  
[grafana schedulers panel](http://localhost:3000/d/yHCu-pG7z/local-schedulers?orgId=1)  
[prometheus](http://localhost:9090)  
[nginx endpoint to call](http://localhost:9999/somePath)

Start the service, go to grafana (admin, MYPASSWORT) schedulers panel, kick of load on the service using `reactor.sh` or `coroutineParallel.sh`, view the metrics appear.