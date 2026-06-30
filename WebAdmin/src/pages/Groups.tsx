import { useEffect, useState } from 'react';
import { App as AntdApp, Avatar, Button, Card, Form, Input, Modal, Space, Table, Tag, Select, List, Tooltip } from 'antd';
import apiClient from '../api/client';

type GroupItem = {
  _id: string;
  name: string;
  description?: string;
  status?: 'active' | 'inactive' | 'archived';
  membersCount?: number;
  settings?: { isPublic?: boolean };
  createdBy?: { _id: string; username: string } | string;
};

export default function Groups() {
  const { message, modal } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<GroupItem[]>([]);
  const [openCreate, setOpenCreate] = useState(false);
  const [openEdit, setOpenEdit] = useState<null | GroupItem>(null);
  const [openRequests, setOpenRequests] = useState<null | GroupItem>(null);
  const [requests, setRequests] = useState<{ user: { _id: string; username: string; email?: string; avatar?: string }; status: string; createdAt: string }[]>([]);
  const [form] = Form.useForm();

  const [search, setSearch] = useState('');

  const fetchData = async (s = search) => {
    setLoading(true);
    try {
      const { data } = await apiClient.get<{ success: boolean; data: { groups: GroupItem[] } }>(
        '/groups', { params: { search: s || undefined } }
      );
      setData(data?.data?.groups || []);
    } finally {
      setLoading(false);
    }
  };

  const fetchRequests = async (groupId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: { requests: { user: { _id: string; username: string; email?: string; avatar?: string }; status: string; createdAt: string }[] } }>(`/groups/${groupId}/join-requests`);
    setRequests(data?.data?.requests || []);
  };

  useEffect(() => {
    fetchData();
  }, []);

  return (
    <Card title="Groups" extra={
      <Space>
        <Input.Search allowClear placeholder="Search by ID or name" value={search} onChange={(e) => setSearch(e.target.value)} onSearch={(val) => fetchData(val)} style={{ width: 280 }} />
      </Space>
    }>
      <Table
        rowKey="_id"
        loading={loading}
        dataSource={data}
        columns={[
          { title: 'Group ID', dataIndex: '_id', render: (id: string) => (
            <Tooltip title={id}><span style={{ fontFamily: 'monospace', fontSize: 12 }}>{id?.substring(0,8)}...</span></Tooltip>
          )},
          { title: 'Name', dataIndex: 'name' },
          { title: 'Description', dataIndex: 'description' },
          { title: 'Status', dataIndex: 'status', render: (s) => <Tag color={s === 'active' ? 'green' : s === 'archived' ? 'red' : 'default'}>{s || 'active'}</Tag> },
          { title: 'Visibility', key: 'visibility', render: (_, r) => (r.settings?.isPublic ? <Tag color="blue">Public</Tag> : <Tag>Private</Tag>) },
          {
            title: 'Actions',
            render: (_, r) => (
              <Space>
                <Button size="small" onClick={() => { setOpenEdit(r); form.setFieldsValue({ name: r.name, description: r.description, status: r.status || 'active', isPublic: r.settings?.isPublic || false, ownerId: typeof r.createdBy === 'string' ? r.createdBy : (r.createdBy as any)?._id }); }}>Edit</Button>
                <Button size="small" onClick={async () => { setOpenRequests(r); await fetchRequests(r._id); }}>Requests</Button>
                <Button size="small" danger onClick={() => modal.confirm({ title: `Delete group ${r.name}?`, onOk: async () => { await apiClient.delete(`/groups/${r._id}`); message.success('Deleted'); fetchData(); } })}>Delete</Button>
                <Button size="small" onClick={async () => {
                  const userId = prompt('Enter userId to add');
                  if (!userId) return;
                  await apiClient.post(`/groups/${r._id}/members`, { userId, role: 'member' });
                  message.success('Member added');
                }}>Add Member</Button>
                <Button size="small" onClick={async () => {
                  const memberId = prompt('Enter memberId to remove');
                  if (!memberId) return;
                  await apiClient.delete(`/groups/${r._id}/members/${memberId}`);
                  message.success('Member removed');
                }}>Remove Member</Button>
              </Space>
            )
          }
        ]}
      />
      <Button type="primary" style={{ position: 'fixed', right: 24, bottom: 24 }} onClick={() => { setOpenCreate(true); form.resetFields(); }}>Create Group</Button>

      <Modal title="Create Group" open={openCreate} onCancel={() => setOpenCreate(false)} onOk={() => form.submit()} confirmLoading={loading}>
        <Form layout="vertical" form={form} onFinish={async (values) => {
          await apiClient.post('/groups', { name: values.name, description: values.description });
          message.success('Group created'); setOpenCreate(false); fetchData();
        }}>
          <Form.Item label="Group Name" name="name" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="Description" name="description"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>

      <Modal title={`Join Requests: ${openRequests?.name}`} open={!!openRequests} onCancel={() => { setOpenRequests(null); setRequests([]); }} footer={null}>
        <List
          dataSource={requests}
          renderItem={(item) => (
            <List.Item
              actions={[
                <Button size="small" type="primary" onClick={async () => { await apiClient.post(`/groups/${openRequests?._id}/join-requests/${item.user._id}`, { action: 'approve' }); message.success('Approved'); fetchRequests(openRequests!._id); }}>Approve</Button>,
                <Button size="small" danger onClick={async () => { await apiClient.post(`/groups/${openRequests?._id}/join-requests/${item.user._id}`, { action: 'reject' }); message.success('Rejected'); fetchRequests(openRequests!._id); }}>Reject</Button>
              ]}
            >
              <List.Item.Meta
                avatar={<Avatar src={item.user.avatar}>
                  {item.user.username?.[0]?.toUpperCase()}
                </Avatar>}
                title={`${item.user.username} (${item.user._id})`}
                description={`Requested at: ${new Date(item.createdAt).toLocaleString()}`}
              />
            </List.Item>
          )}
          locale={{ emptyText: 'No pending requests' }}
        />
      </Modal>

      <Modal title={`Edit Group: ${openEdit?.name}`} open={!!openEdit} onCancel={() => setOpenEdit(null)} onOk={() => form.submit()} confirmLoading={loading}>
        <Form layout="vertical" form={form} onFinish={async (values) => {
          // Update basic info
          await apiClient.put(`/groups/${openEdit?._id}`, { name: values.name, description: values.description });
          // Update status (separate endpoint)
          if (values.status && values.status !== openEdit?.status) {
            await apiClient.put(`/groups/status`, { status: values.status, groupId: openEdit?._id });
          }
          // Update public/private via settings
          if (typeof values.isPublic === 'boolean' && values.isPublic !== (openEdit?.settings?.isPublic || false)) {
            await apiClient.put(`/groups/${openEdit?._id}`, { settings: { isPublic: values.isPublic } });
          }
          // Transfer ownership if changed
          if (values.ownerId && values.ownerId !== (typeof openEdit?.createdBy === 'string' ? openEdit?.createdBy : (openEdit?.createdBy as any)?._id)) {
            await apiClient.put(`/groups/${openEdit?._id}/owner`, { newOwnerId: values.ownerId });
          }
          message.success('Updated'); setOpenEdit(null); fetchData();
        }}>
          <Form.Item label="Group Name" name="name" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="Description" name="description"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item label="Status" name="status" rules={[{ required: true }]}>
            <Select options={[
              { value: 'active', label: 'Active' },
              { value: 'inactive', label: 'Inactive' },
              { value: 'archived', label: 'Archived' },
            ]} />
          </Form.Item>
          <Form.Item label="Visibility" name="isPublic" valuePropName="value">
            <Select options={[
              { value: true, label: 'Public' },
              { value: false, label: 'Private' },
            ]} />
          </Form.Item>
          <Form.Item label="Owner (User ID)" name="ownerId">
            <Input placeholder="Enter new owner userId" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}


