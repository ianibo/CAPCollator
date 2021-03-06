

// Added by the Spring Security Core plugin:
grails.plugin.springsecurity.userLookup.userDomainClassName = 'capcollator.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'capcollator.UserRole'
grails.plugin.springsecurity.authority.className = 'capcollator.Role'

// Remember that these patterns are based on the controller/action not the URL Mappings
// So use /home/topic instead of /topics/**
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
	[pattern: '/',                  access: ['permitAll']],
        [pattern: '/actuator/**',       access: ['ROLE_ADMIN', 'isFullyAuthenticated()']],
	[pattern: '/setup/**',          access: ['permitAll']],
	[pattern: '/logout',            access: ['permitAll']],
	[pattern: '/error',             access: ['permitAll']],
	[pattern: '/subscriptions',     access: ['permitAll']],
	[pattern: '/subscriptions/**',  access: ['permitAll']],
	[pattern: '/alert',             access: ['permitAll']],
	[pattern: '/alert/**',          access: ['permitAll']],
	[pattern: '/api/**',            access: ['permitAll']],
	[pattern: '/home/index',        access: ['permitAll']],
	[pattern: '/home/topic',        access: ['permitAll']],
	[pattern: '/home/about',        access: ['permitAll']],
	[pattern: '/home/explorer1',    access: ['permitAll']],
	[pattern: '/home/info',         access: ['permitAll']],
	[pattern: '/index',             access: ['permitAll']],
        [pattern: '/hubClient/**',      access: ['permitAll']],
	[pattern: '/index.gsp',         access: ['permitAll']],
	[pattern: '/shutdown',          access: ['permitAll']],
	[pattern: '/assets/**',         access: ['permitAll']],
	[pattern: '/**/js/**',          access: ['permitAll']],
	[pattern: '/**/css/**',         access: ['permitAll']],
	[pattern: '/**/images/**',      access: ['permitAll']],
	[pattern: '/**/favicon.ico',    access: ['permitAll']]
]

grails.plugin.springsecurity.filterChain.chainMap = [
	[pattern: '/assets/**',      filters: 'none'],
	[pattern: '/**/js/**',       filters: 'none'],
	[pattern: '/**/css/**',      filters: 'none'],
	[pattern: '/**/images/**',   filters: 'none'],
	[pattern: '/**/favicon.ico', filters: 'none'],
	[pattern: '/**',             filters: 'JOINED_FILTERS']
]


rabbitmq = [
  connections:[
    [
      name: 'localRMQ',
      host: System.getenv('RABBIT_HOST')?:'rabbitmq',
      username: System.getenv('CAP_RABBIT_USER')?:'cap',
      password: System.getenv('CAP_RABBIT_PASS')?:'cap',
      automaticReconnect: true,
      requestedHeartbeat: 120
    ]
  ]
]

println("\n\nCC_ES_HOST:: ${System.getenv('CC_ES_HOST')}\n\n");

elasticSearch = [
  client:[
    mode: 'transport',
    hosts:[
      [ 
        host: System.getenv('CC_ES_HOST')?:'elasticsearch',
        port: 9300
      ]
    ]
  ]
]

eshost = System.getenv('CC_ES_HOST')?:'elasticsearch';

println("ES config block: ${elasticSearch}");

