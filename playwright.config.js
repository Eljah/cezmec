import {defineConfig} from '@playwright/test';
export default defineConfig({
  testDir:'tests/e2e', timeout:30000, workers:1,
  use:{baseURL:'http://127.0.0.1:8080',viewport:{width:1440,height:1100},reducedMotion:'reduce',trace:'retain-on-failure',screenshot:'only-on-failure'},
  reporter:[['list'],['html',{open:'never'}]],
  webServer:{command:'java -jar target/cezmec-0.1.0.jar "--spring.datasource.url=jdbc:h2:mem:cezmec-e2e;DB_CLOSE_DELAY=-1"',url:'http://127.0.0.1:8080/api/stats',reuseExistingServer:false,timeout:60000},
});
