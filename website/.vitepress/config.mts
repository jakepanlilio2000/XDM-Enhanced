import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'XDM Enhanced',
  description: 'High-speed, modular, extensible open-source download manager for desktop.',
  base: '/XDM-Enhanced/',
  cleanUrls: true,
  lastUpdated: true,
  srcExclude: ['**/public/**'],

  sitemap: {
    hostname: 'https://jakepanlilio2000.github.io',
    transformItems(items) {
      return items.map(item => ({
        ...item,
        url: item.url.startsWith('XDM-Enhanced/') ? item.url : 'XDM-Enhanced/' + item.url.replace(/^\//, '')
      }))
    }
  },

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/XDM-Enhanced/assets/site/branding/xdm-logo.svg' }],
    ['meta', { name: 'theme-color', content: '#2563eb' }],
    ['meta', { name: 'author', content: 'XDM Enhanced Project Maintainers' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:title', content: 'XDM Enhanced — High-Speed Modular Download Manager' }],
    ['meta', { property: 'og:description', content: 'An independent open-source download accelerator with BitTorrent 2.0, dynamic FlatLaf skins, decoupled Java plugins, and companion browser extensions.' }],
    ['meta', { property: 'og:image', content: '/XDM-Enhanced/assets/site/branding/app-icon-template.svg' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }]
  ],

  themeConfig: {
    logo: {
      src: '/assets/site/branding/xdm-logo.svg',
      alt: 'XDM Enhanced Logo'
    },
    siteTitle: 'XDM Enhanced',

    nav: [
      { text: 'Home', link: '/' },
      { text: 'Features', link: '/features' },
      { text: 'Downloads', link: '/downloads' },
      { text: 'Extensions', link: '/extensions' },
      {
        text: 'Guides',
        items: [
          { text: 'Documentation Home', link: '/docs/' },
          { text: 'Getting Started', link: '/docs/getting-started' },
          { text: 'Browser Setup', link: '/docs/browser-integration' },
          { text: 'BitTorrent & Magnets', link: '/docs/torrent-guide' },
          { text: 'Post-Download Rules', link: '/docs/automation' },
          { text: 'Troubleshooting & FAQ', link: '/troubleshooting' },
          { text: 'Community Wiki (Staged)', link: 'https://github.com/jakepanlilio2000/XDM-Enhanced/wiki' }
        ]
      },
      {
        text: 'Developers',
        items: [
          { text: 'Developer Portal', link: '/developer/' },
          { text: 'Plugin API v2 Guide', link: '/developer/plugins' },
          { text: 'Skin Customization Guide', link: '/developer/skins' },
          { text: 'Building from Source', link: '/developer/building' },
          { text: 'Contributing Guide', link: '/contribute' },
          { text: 'Authoritative Specs (docs/)', link: 'https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/docs' }
        ]
      },
      {
        text: 'More',
        items: [
          { text: 'UI Screenshots', link: '/screenshots' },
          { text: 'Release Changelog', link: '/releases' },
          { text: 'About & Attribution', link: '/about' }
        ]
      }
    ],

    sidebar: {
      '/docs/': [
        {
          text: 'User Guides',
          collapsed: false,
          items: [
            { text: 'Overview', link: '/docs/' },
            { text: 'Getting Started', link: '/docs/getting-started' },
            { text: 'Browser Setup', link: '/docs/browser-integration' },
            { text: 'BitTorrent & Magnets', link: '/docs/torrent-guide' },
            { text: 'Post-Download Rules', link: '/docs/automation' },
            { text: 'Troubleshooting & FAQ', link: '/troubleshooting' }
          ]
        },
        {
          text: 'Quick Links',
          items: [
            { text: 'Downloads & Releases', link: '/downloads' },
            { text: 'Features Breakdown', link: '/features' },
            { text: 'UI Screenshots', link: '/screenshots' }
          ]
        }
      ],
      '/developer/': [
        {
          text: 'Developer Platform',
          collapsed: false,
          items: [
            { text: 'Developer Overview', link: '/developer/' },
            { text: 'Plugin API v2 Specification', link: '/developer/plugins' },
            { text: 'Skin Customization System', link: '/developer/skins' },
            { text: 'Building from Source', link: '/developer/building' },
            { text: 'Contributing Guide', link: '/contribute' }
          ]
        },
        {
          text: 'Project Resources',
          items: [
            { text: 'Changelog & Releases', link: '/releases' },
            { text: 'About & Lineage', link: '/about' }
          ]
        }
      ]
    },

    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: {
                buttonText: 'Search documentation',
                buttonAriaLabel: 'Search documentation'
              },
              modal: {
                noResultsText: 'No relevant results found',
                resetButtonTitle: 'Reset search query',
                footer: {
                  selectText: 'to select',
                  navigateText: 'to navigate',
                  closeText: 'to close'
                }
              }
            }
          }
        }
      }
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/jakepanlilio2000/XDM-Enhanced' }
    ],

    footer: {
      message: 'Released under the GNU GPL v3.0 / Apache-2.0 Licenses. Independently maintained fork of Xtreme Download Manager by subhra74.',
      copyright: 'Copyright © 2026 XDM Enhanced Contributors'
    },

    docFooter: {
      prev: 'Previous Page',
      next: 'Next Page'
    },

    outline: {
      level: [2, 3],
      label: 'On this page'
    }
  }
})
