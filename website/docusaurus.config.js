const lightCodeTheme = require("prism-react-renderer/themes/github");
const darkCodeTheme = require("prism-react-renderer/themes/dracula");

/** @type {import('@docusaurus/types').DocusaurusConfig} */
module.exports = {
  title: "Hyperdrive",
  tagline: "Kotlin Multiplatform Extensions",
  url: "https://hyperdrive.tools",
  baseUrl: "/",
  onBrokenLinks: "warn",
  onBrokenMarkdownLinks: "warn",
  favicon: "img/favicon.ico",
  organizationName: "Brightify", // Usually your GitHub org/user name.
  projectName: "hyperdrive-kt", // Usually your repo name.
  themeConfig: {
    navbar: {
      title: "Hyperdrive",
      logo: {
        alt: "Hyperdrive Logo",
        src: "img/logo.svg",
      },
      items: [
        {
          type: "doc",
          docId: "getting-started/intro",
          position: "left",
          label: "Getting Started",
        },
        {
          type: "doc",
          docId: "multiplatformx/intro",
          position: "left",
          label: "MultiplatformX",
        },
        {
          type: "doc",
          docId: "krpc/intro",
          position: "left",
          label: "kRPC",
        },
        {
          type: "doc",
          docId: "tutorials/intro",
          position: "left",
          label: "Tutorials",
        },
        { to: "/blog", label: "Blog", position: "left" },
        {
          href: "https://hyperdrive.tools/reference",
          label: "Reference",
          position: "left",
        },
        {
          href: "https://github.com/Brightify/hyperdrive-kt",
          label: "GitHub",
          position: "right",
        },
      ],
    },
    footer: {
      style: "dark",
      links: [
        {
          title: "Docs",
          items: [
            {
              label: "Tutorials",
              to: "/docs/tutorials/intro",
            },
          ],
        },
        {
          title: "Community",
          items: [
            {
              label: "Stack Overflow",
              href: "https://stackoverflow.com/questions/tagged/hyperdrive",
            },
            {
              label: "Discord",
              href: "https://discordapp.com/invite/Brightify",
            },
            {
              label: "Twitter",
              href: "https://twitter.com/BrightifyOrg",
            },
          ],
        },
        {
          title: "More",
          items: [
            {
              label: "Blog",
              to: "/blog",
            },
            {
              label: "GitHub",
              href: "https://github.com/Brightify/hyperdrive-kt",
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Brightify s.r.o. Built with Docusaurus.`,
    },
    prism: {
      theme: lightCodeTheme,
      darkTheme: darkCodeTheme,
      additionalLanguages: ["kotlin", "swift"],
    },
  },
  presets: [
    [
      "@docusaurus/preset-classic",
      {
        docs: {
          sidebarPath: require.resolve("./sidebars.js"),
          // Please change this to your repo.
          editUrl:
            "https://github.com/Brightify/hyperdrive-kt/edit/main/website/",
        },
        blog: {
          showReadingTime: true,
          // Please change this to your repo.
          editUrl:
            "https://github.com/Brightify/hyperdrive-kt/edit/main/website/blog/",
        },
        theme: {
          customCss: require.resolve("./src/css/custom.css"),
        },
      },
    ],
  ],
};
