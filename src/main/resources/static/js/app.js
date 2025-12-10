document.addEventListener('DOMContentLoaded', function () {
    jQuery("#minecraftVersions").DataTable({
        paging: false,
        order: [[3, 'desc']],
        fixedHeader: true,
        columnControl: [
            {
                target: 0,
                content: ['order', ['searchList']]
            },
            {
                target: 1,
                content: ['search']
            }
        ],
        ordering: {
            indicators: false,
            handler: false
        }
    });
});
