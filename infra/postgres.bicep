// Provisions the Azure Database for PostgreSQL Flexible Server for TeeUp (EME-309).
// Deploy with deploy-postgres.sh, which also creates the teeup database,
// the dedicated teeup_api login, and applies EF Core migrations.

@description('Name of the PostgreSQL Flexible Server. Must be globally unique.')
param serverName string

@description('Azure region for the server.')
param location string = resourceGroup().location

@description('Server admin username. Never reused from local dev.')
param administratorLogin string

@secure()
@description('Server admin password.')
param administratorLoginPassword string

@description('Caller IP address allowed through the firewall for migrations/verification.')
param callerIpAddress string

@description('Allow any Azure service (including Brandon''s App Service) to reach this server.')
param allowAzureServices bool = true

resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: serverName
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: administratorLogin
    administratorLoginPassword: administratorLoginPassword
    storage: {
      storageSizeGB: 32
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
  }
}

resource teeupDatabase 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgresServer
  name: 'teeup'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

resource allowAzureServicesRule 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = if (allowAzureServices) {
  parent: postgresServer
  name: 'AllowAllAzureServicesAndResourcesWithinAzureIps'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource allowCallerRule 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgresServer
  name: 'AllowCallerIP'
  properties: {
    startIpAddress: callerIpAddress
    endIpAddress: callerIpAddress
  }
}

output serverFqdn string = postgresServer.properties.fullyQualifiedDomainName
